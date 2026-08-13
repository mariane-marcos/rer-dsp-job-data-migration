package br.car.dsp_batch.layer.service;

import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.sync.SyncState;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LayerChangeDetectionServiceTest {

    private final LayerChangeDetectionService service =
            new LayerChangeDetectionService(
                    new WatermarkChangeDetectionEngine(mock(SyncStateRepository.class)));

    @Test
    void normalizeId_ConvertsNumberToString() {
        assertEquals("42", service.normalizeId(42L));
        assertEquals("42", service.normalizeId(42));
    }

    @Test
    void normalizeId_KeepsString() {
        assertEquals("ABC123", service.normalizeId("ABC123"));
    }

    @Test
    void requirePositiveSrid_RejectsInvalidMetadata() {
        LayerTableMetadata metadata = metadataWithSrid(0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LayerFeaturePersistenceService.requirePositiveSrid(metadata)
        );
        assertTrue(ex.getMessage().contains("srid"));
    }

    @Test
    void requirePositiveSrid_AcceptsValidMetadata() {
        LayerTableMetadata metadata = metadataWithSrid(4674);
        assertDoesNotThrow(() -> LayerFeaturePersistenceService.requirePositiveSrid(metadata));
    }

    @Test
    void buildUpdatedAtFilterSql_RequiresNotNullWithoutWatermark() {
        assertEquals(
                "data_atualizacao IS NOT NULL",
                LayerChangeDetectionService.buildUpdatedAtFilterSql("data_atualizacao", null)
        );
    }

    @Test
    void buildUpdatedAtFilterSql_UsesSourceColumnAndInstant() {
        Instant watermark = Instant.parse("2026-08-10T15:00:00Z");
        String sql = LayerChangeDetectionService.buildUpdatedAtFilterSql("data_atualizacao", watermark);
        assertEquals(
                "data_atualizacao IS NOT NULL AND data_atualizacao > TIMESTAMP WITH TIME ZONE '2026-08-10T15:00:00Z'",
                sql
        );
    }

    @Test
    void shouldRunOrphanCheck_WhenNoState() {
        assertTrue(service.shouldRunOrphanCheck(null));
    }

    @Test
    void shouldRunOrphanCheck_WhenWatermarkMissing() {
        SyncState state = new SyncState(
                "dsp_teste", "src.teste", null, null, null, Instant.now());
        assertTrue(service.shouldRunOrphanCheck(state));
    }

    @Test
    void shouldRunOrphanCheck_WhenLastCheckTooOld() {
        SyncState state = new SyncState(
                "dsp_teste",
                "src.teste",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                1L,
                Instant.now().minus(LayerChangeDetectionService.ORPHAN_CHECK_INTERVAL).minusSeconds(1)
        );
        assertTrue(service.shouldRunOrphanCheck(state));
    }

    @Test
    void shouldRunOrphanCheck_WhenLastCheckRecent() {
        SyncState state = new SyncState(
                "dsp_teste",
                "src.teste",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                1L,
                Instant.now()
        );
        assertFalse(service.shouldRunOrphanCheck(state));
    }

    private LayerTableMetadata metadataWithSrid(int srid) {
        return new LayerTableMetadata(
                "dsp_teste",
                "teste",
                new QualifiedTable("src", "teste"),
                new QualifiedTable("dsp", "teste"),
                "id",
                "geom",
                "cod_imovel",
                "data_atualizacao",
                "nome",
                srid,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                        new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                        new ColumnMetadata("data_atualizacao", "timestamptz", null, null, null, false, false),
                        new ColumnMetadata("notes", "text", null, null, null, true, false),
                        new ColumnMetadata("geom", "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
