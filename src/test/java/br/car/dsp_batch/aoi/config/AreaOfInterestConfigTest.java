package br.car.dsp_batch.aoi.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaOfInterestConfigTest {

    @Test
    void validate_AcceptsOptionalPersistColumns() {
        AreaOfInterestConfig config = baseConfig();
        config.setPersistColumns(List.of("management_plan"));

        assertDoesNotThrow(config::validate);
    }

    @Test
    void validate_AcceptsBusinessOnlyPersistColumns() {
        AreaOfInterestConfig config = baseConfig();
        config.setBusinessOnlyPersistColumns(List.of("theme_1", "theme_2"));

        assertDoesNotThrow(config::validate);
    }

    @Test
    void validate_RejectsPersistColumnDuplicatingRequiredMapping() {
        AreaOfInterestConfig config = baseConfig();
        config.setPersistColumns(List.of("commune_id"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("commune_id"));
    }

    @Test
    void validate_RejectsDuplicateBetweenPersistAndBusinessOnlyLists() {
        AreaOfInterestConfig config = baseConfig();
        config.setPersistColumns(List.of("category"));
        config.setBusinessOnlyPersistColumns(List.of("category"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("category"));
    }

    @Test
    void validate_RejectsCanonicalTargetNameInBusinessOnlyPersistColumns() {
        AreaOfInterestConfig config = baseConfig();
        config.setBusinessOnlyPersistColumns(List.of("theme_1", "area"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("area"));
    }

    @Test
    void validate_RequiresMandatoryColumnMappings() {
        AreaOfInterestConfig config = baseConfig();
        config.setCreationDateColumn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("creation-date-column"));
    }

    private static AreaOfInterestConfig baseConfig() {
        AreaOfInterestConfig config = new AreaOfInterestConfig();
        config.setSourceTable("conservation.conservation_units");
        config.setTargetTable("dsp.area_of_interest");
        config.setPrimaryKey("id");
        config.setCreationDateColumn("creation_date");
        config.setUpdatedAtColumn("updated_at");
        config.setCommuneIdColumn("commune_id");
        config.setTotalAreaColumn("total_area_ha");
        config.setGeometryColumn("geom");
        config.setSrid(4674);
        return config;
    }
}
