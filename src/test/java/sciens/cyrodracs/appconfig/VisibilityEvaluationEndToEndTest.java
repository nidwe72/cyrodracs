package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.DataFormEvaluationService;
import sciens.cyrodracs.camera.CameraProducer;
import sciens.cyrodracs.camera.CameraProducerRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for BooleanInjectable visibility evaluation.
 *
 * Prerequisites (seeded by CameraFormSeederTest + FujiLensMountInsertTest):
 * - Expression "isCameraProducerSelected" (BOOLEAN_VALUE)
 * - VisibilityRule on "cameraLensMount2CameraProducer" referencing that expression
 * - Producers, lens mounts, and mappings in DB
 */
@SpringBootTest
class VisibilityEvaluationEndToEndTest {

    @Autowired DataFormEvaluationService evaluationService;
    @Autowired AppConfigStore appConfigStore;
    @Autowired CameraProducerRepository producerRepo;

    @BeforeEach
    void reloadConfig() {
        appConfigStore.reload();
    }

    @Test
    @Transactional
    void mountElementHiddenWhenNoProducer() {
        Map<String, ElementState> result = evaluationService.evaluate(
                "camera", null, "producer", Map.of());

        ElementState mountState = result.get("cameraLensMount2CameraProducer");
        assertNotNull(mountState, "mount element should be in evaluation result");
        assertFalse(mountState.isVisible(), "mount should be hidden when no producer selected");
    }

    @Test
    @Transactional
    void mountElementVisibleWhenProducerSelected() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        Map<String, ElementState> result = evaluationService.evaluate(
                "camera", null, "producer",
                Map.of("producer", fuji.getId().toString()));

        ElementState mountState = result.get("cameraLensMount2CameraProducer");
        assertNotNull(mountState, "mount element should be in evaluation result");
        assertTrue(mountState.isVisible(), "mount should be visible when producer selected");
        assertNotNull(mountState.getOptions(), "mount options should be populated");
        assertFalse(mountState.getOptions().isEmpty(), "mount should have options for Fuji");
    }

    @Test
    @Transactional
    void initialLoadEvaluatesAllElementsWithRules() {
        // changedElement = null → initial load
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        Map<String, ElementState> result = evaluationService.evaluate(
                "camera", null, null,
                Map.of("producer", fuji.getId().toString()));

        // Should include mount (has visibilityRule + entityProviderRef)
        assertTrue(result.containsKey("cameraLensMount2CameraProducer"),
                "initial load should evaluate mount element");

        // Should include producer (has entityProviderRef)
        assertTrue(result.containsKey("producer"),
                "initial load should evaluate producer element");
    }

    @Test
    @Transactional
    void initialLoadWithEmptyFormStateHidesMount() {
        Map<String, ElementState> result = evaluationService.evaluate(
                "camera", null, null, Map.of());

        ElementState mountState = result.get("cameraLensMount2CameraProducer");
        assertNotNull(mountState);
        assertFalse(mountState.isVisible(), "mount should be hidden on initial load without producer");
    }
}
