package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.GridDataService;
import sciens.cyrodracs.camera.CameraProducer;
import sciens.cyrodracs.camera.CameraProducerRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test: calls GridDataService for cameraProducer / lensMountMappings
 * with Fuji (id from DB) and verifies the GRID returns the correct filtered rows.
 */
@SpringBootTest
class GridDataEndToEndTest {

    @Autowired GridDataService gridDataService;
    @Autowired CameraProducerRepository producerRepo;
    @Autowired AppConfigStore appConfigStore;

    @Test
    @Transactional(readOnly = true)
    void gridReturnsFilteredMountsForFuji() {
        appConfigStore.reload();  // pick up config data seeded by GridConfigSeederTest
        CameraProducer fuji = producerRepo.findByName("Fuji")
                .orElseThrow(() -> new IllegalStateException(
                    "Fuji not found — run FujiLensMountInsertTest first"));

        Map<String, String> formState = Map.of(
                "name", "Fuji",
                "foundationYear", "1934-01"
        );

        GridDataService.GridPagedResult result = gridDataService.getGridData(
                "cameraProducer", "lensMountMappings",
                fuji.getId(), formState,
                0, 10);

        // Fuji has 2 mount mappings: M42 and X-Mount
        assertEquals(2, result.totalCount(),
                "Expected 2 CameraLensMount2CameraProducer rows for Fuji");
        assertEquals(2, result.items().size());

        // Verify rows have the expected columns
        for (var row : result.items()) {
            assertNotNull(row.get("id"), "Row should have id");
            assertNotNull(row.get("cameraLensMount"), "Row should have cameraLensMount column");
            assertNotNull(row.get("cameraProducer"), "Row should have cameraProducer column");
        }

        // Check that mount names are M42 and X-Mount (sorted by cameraLensMount.name ASC)
        var mountNames = result.items().stream()
                .map(row -> row.get("cameraLensMount").toString())
                .toList();
        assertTrue(mountNames.contains("M42"), "Should contain M42");
        assertTrue(mountNames.contains("X-Mount"), "Should contain X-Mount");
    }

    @Test
    @Transactional(readOnly = true)
    void gridReturnsZeroRowsInCreateNewMode() {
        appConfigStore.reload();
        // Brand-new (transient) producer — not yet persisted, so no
        // CameraLensMount2CameraProducer rows can possibly reference it.
        // producerMountFilter detects null editor / null id and emits a
        // match-nothing predicate (`cameraProducer.id IS NULL`).
        GridDataService.GridPagedResult result = gridDataService.getGridData(
                "cameraProducer", "lensMountMappings",
                null, Map.of(),
                0, 10);

        assertEquals(0, result.totalCount(),
                "Create-new mode must return zero rows — the new producer has no mappings yet");
    }
}
