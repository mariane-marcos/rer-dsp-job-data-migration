package br.car.dsp_batch.layer.ddl;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerTableDdlBuilderTest {

    private final LayerTableDdlBuilder builder = new LayerTableDdlBuilder(new PostgresTypeMapper());

    @Test
    void buildCreateTable_IncludesPrimaryKeyGeometryAreaOfInterestIdAndUpdatedAt() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS dsp.parcelas"));
        assertTrue(ddl.contains("\"id_parcela\" varchar(80)"));
        assertTrue(ddl.contains("\"nome\" varchar(255)"));
        assertTrue(ddl.contains("\"area_of_interest_id\" varchar(80)"));
        assertTrue(ddl.contains("\"updated_at\""));
        assertTrue(ddl.contains("\"geom\" geometry(Geometry, 4674)"));
        assertTrue(!ddl.contains("\"the_geom\""));
        assertTrue(!ddl.contains("\"data_atualizacao\""));
        assertTrue(ddl.contains("PRIMARY KEY (\"id_parcela\")"));
        assertTrue(!ddl.contains("\"cod_imovel\""));
    }

    @Test
    void buildCreateTable_MigratesOnlyChosenGeometryColumn() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "varchar", 80, null, null, false, false),
                new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("centroid", "geometry", null, null, null, true, true),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                columns,
                List.of(),
                "1=1"
        );

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("\"geom\" geometry(Geometry, 4674)"));
        assertTrue(!ddl.contains("\"centroid\""));
        assertTrue(!ddl.contains("\"the_geom\""));
    }

    @Test
    void buildEnsureUpdatedAtColumn_AddsCanonicalColumn() {
        String ddl = builder.buildEnsureUpdatedAtColumn(sampleMetadata());
        assertTrue(ddl.contains("ALTER TABLE dsp.parcelas ADD COLUMN IF NOT EXISTS \"updated_at\" timestamptz"));
    }

    @Test
    void buildGeometryIndex_UsesGistOnCanonicalGeomColumn() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildGeometryIndex(metadata);

        assertTrue(ddl.contains("CREATE INDEX IF NOT EXISTS"));
        assertTrue(ddl.contains("USING GIST (\"geom\")"));
    }

    @Test
    void buildAreaOfInterestIdIndex_CreatesBtreeOnCanonicalColumn() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildAreaOfInterestIdIndex(metadata);

        assertTrue(ddl.contains("CREATE INDEX IF NOT EXISTS"));
        assertTrue(ddl.contains("(\"area_of_interest_id\")"));
    }

    @Test
    void buildUpdatedAtIndex_CreatesBtreeOnCanonicalColumn() {
        String ddl = builder.buildUpdatedAtIndex(sampleMetadata());
        assertTrue(ddl.contains("(\"updated_at\")"));
    }

    @Test
    void buildSecondaryIndexes_SkipsDuplicateGistOnGeometry() {
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_geo_idx", List.of("the_geom"), "gist", false)),
                "1=1"
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    @Test
    void buildSecondaryIndexes_SkipsRedundantIndexOnRenamedAreaOfInterestColumn() {
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_aoi_idx", List.of("cod_imovel"), "btree", false)),
                "1=1"
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    @Test
    void buildSecondaryIndexes_KeepsIndexOnNonAoiColumn() {
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_nome_idx", List.of("nome"), "btree", false)),
                "1=1"
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.size() == 1);
        assertTrue(indexes.getFirst().contains("(\"nome\")"));
    }

    @Test
    void buildSecondaryIndexes_MapsAoiColumnInsideCompositeIndex() {
        LayerTableMetadata metadata = sampleMetadata();
        List<String> indexes = builder.buildSecondaryIndexes(new LayerTableMetadata(
                metadata.layerKey(),
                metadata.layerName(),
                metadata.sourceTable(),
                metadata.targetTable(),
                metadata.primaryKeyColumn(),
                metadata.geometryColumn(),
                metadata.areaOfInterestIdSourceColumn(),
                metadata.updatedAtSourceColumn(),
                metadata.srid(),
                metadata.columns(),
                List.of(new IndexMetadata(
                        "parcelas_aoi_nome_idx",
                        List.of("cod_imovel", "nome"),
                        "btree",
                        false
                )),
                metadata.whereClause()
        ));

        assertTrue(indexes.size() == 1);
        assertTrue(indexes.getFirst().contains("area_of_interest_id"));
        assertTrue(indexes.getFirst().contains("nome"));
    }

    @Test
    void buildSecondaryIndexes_SkipsIndexWhenMappedColumnMissingOnTarget() {
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_orphan_idx", List.of("coluna_inexistente"), "btree", false)),
                "1=1"
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    @Test
    void buildStatements_ReturnsCreateTableAndIndexes() {
        LayerTableMetadata metadata = sampleMetadata();

        List<String> statements = builder.buildStatements(metadata);

        assertTrue(statements.size() >= 4);
        assertTrue(statements.getFirst().startsWith("CREATE TABLE"));
        assertTrue(statements.stream().anyMatch(s -> s.contains("ADD COLUMN IF NOT EXISTS")));
        assertTrue(statements.stream().anyMatch(s -> s.contains("USING GIST")));
        assertTrue(statements.stream().anyMatch(s -> s.contains(LayerConfig.AREA_OF_INTEREST_ID_COLUMN)));
        assertTrue(statements.stream().anyMatch(s -> s.contains(LayerConfig.UPDATED_AT_COLUMN)
                && s.startsWith("CREATE INDEX")));
    }

    private LayerTableMetadata sampleMetadata() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "varchar", 80, null, null, false, false),
                new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        return new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_atualizacao",
                4674,
                columns,
                List.of(),
                "1=1"
        );
    }
}
