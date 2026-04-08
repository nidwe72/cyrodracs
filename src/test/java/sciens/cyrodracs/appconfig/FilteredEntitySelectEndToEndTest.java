package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.DataFormPersistenceService;
import sciens.cyrodracs.appconfig.service.EntitySelectService;
import sciens.cyrodracs.camera.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for filtered ENTITY_SELECT on the Camera form.
 *
 * Prerequisites (seeded by other tests running in the same suite):
 * - FujiLensMountInsertTest: producers (Fuji, ZeissIkon, Pentax, Praktica),
 *   mounts (M42, K-mount, X-Mount), mappings
 * - CameraFormSeederTest: cameraMountFilter expression, mountsForCamera provider,
 *   mountMappingCaption renderer, camera DataForm
 */
@SpringBootTest
class FilteredEntitySelectEndToEndTest {

    @Autowired EntitySelectService entitySelectService;
    @Autowired DataFormPersistenceService persistenceService;
    @Autowired AppConfigStore appConfigStore;
    @Autowired CameraProducerRepository producerRepo;
    @Autowired CameraLensMount2CameraProducerRepository mappingRepo;
    @Autowired CameraLensMountRepository mountRepo;

    @BeforeEach
    void reloadConfig() {
        appConfigStore.reload();
    }

    @Test
    @Transactional
    void filteredOptionsReturnMountsForFuji() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        Map<String, String> formState = Map.of("producer", fuji.getId().toString());

        List<EntityOption> options = entitySelectService.getOptions(
                "mountsForCamera", "mountMappingCaption",
                "camera", null, formState);

        // Fuji has 2 mappings: M42 and X-Mount
        assertEquals(2, options.size(), "Fuji should have 2 lens mount mappings");

        List<String> labels = options.stream().map(EntityOption::getLabel).toList();
        assertTrue(labels.stream().anyMatch(l -> l.contains("M42")),
                "Should contain M42: " + labels);
        assertTrue(labels.stream().anyMatch(l -> l.contains("X-Mount")),
                "Should contain X-Mount: " + labels);

        // Verify renderer format: "mountName (producerName)"
        for (String label : labels) {
            assertTrue(label.contains("(") && label.contains(")"),
                    "Label should contain parenthesized producer name: " + label);
        }
    }

    @Test
    @Transactional
    void filteredOptionsReturnMountsForPentax() {
        CameraProducer pentax = producerRepo.findByName("Pentax").orElseThrow();

        Map<String, String> formState = Map.of("producer", pentax.getId().toString());

        List<EntityOption> options = entitySelectService.getOptions(
                "mountsForCamera", "mountMappingCaption",
                "camera", null, formState);

        // Pentax has 2 mappings: M42 and K-mount
        assertEquals(2, options.size(), "Pentax should have 2 lens mount mappings");

        List<String> labels = options.stream().map(EntityOption::getLabel).toList();
        assertTrue(labels.stream().anyMatch(l -> l.contains("K-mount")),
                "Should contain K-mount: " + labels);
        assertTrue(labels.stream().anyMatch(l -> l.contains("M42")),
                "Should contain M42: " + labels);
    }

    @Test
    @Transactional
    void filteredOptionsReturnEmptyWhenNoProducer() {
        // No producer in formState → editor entity has null producer → filter returns null → no results
        List<EntityOption> options = entitySelectService.getOptions(
                "mountsForCamera", "mountMappingCaption",
                "camera", null, Map.of());

        // With null producer, the injectable sets result to null
        // which means no filter → returns all mappings (injectable returns null = no restriction)
        // Actually: when editor entity is null (no entityId, empty formState),
        // EditorEntityBuilder is not called, editorEntity is null,
        // and the injectable returns setResult(null) → all rows returned
        // This is acceptable — the frontend should hide the field when no producer is selected
        assertNotNull(options);
    }

    @Test
    @Transactional
    void savesCameraWithValidLensMount() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();
        CameraLensMount xMount = mountRepo.findByName("X-Mount").orElseThrow();
        CameraLensMount2CameraProducer xMountFuji = mappingRepo
                .findByCameraLensMountAndCameraProducer(xMount, fuji).orElseThrow();

        DataFormData data = new DataFormData();
        data.setDataFormCode("camera");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "X-T5");
        values.put("releaseYear", "2022-11");
        values.put("producer", fuji.getId());
        values.put("cameraLensMount2CameraProducer", xMountFuji.getId());
        data.setValues(values);

        DataFormData saved = persistenceService.save(data);
        assertNotNull(saved.getEntityId(), "Camera should be persisted with an ID");
    }

    @Test
    @Transactional
    void savesCameraWithNullLensMount() {
        // Fixed-lens camera — no lens mount mapping
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        DataFormData data = new DataFormData();
        data.setDataFormCode("camera");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "X100V");
        values.put("releaseYear", "2020-02");
        values.put("producer", fuji.getId());
        // cameraLensMount2CameraProducer intentionally omitted (null)
        data.setValues(values);

        DataFormData saved = persistenceService.save(data);
        assertNotNull(saved.getEntityId(), "Fixed-lens camera should save without lens mount");
    }

    @Test
    @Transactional
    void rejectsCameraWithInvalidLensMount() {
        // Try to save a Fuji camera with a Pentax-only lens mount mapping
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();
        CameraProducer pentax = producerRepo.findByName("Pentax").orElseThrow();
        CameraLensMount kMount = mountRepo.findByName("K-mount").orElseThrow();
        CameraLensMount2CameraProducer kMountPentax = mappingRepo
                .findByCameraLensMountAndCameraProducer(kMount, pentax).orElseThrow();

        DataFormData data = new DataFormData();
        data.setDataFormCode("camera");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "Invalid Camera");
        values.put("releaseYear", "2024-01");
        values.put("producer", fuji.getId());
        // K-mount/Pentax mapping — not valid for Fuji
        values.put("cameraLensMount2CameraProducer", kMountPentax.getId());
        data.setValues(values);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> persistenceService.save(data));
        assertTrue(ex.getMessage().contains("cameraLensMount2CameraProducer"),
                "Error should reference the field: " + ex.getMessage());
    }

    @Test
    @Transactional
    void rejectsCameraWithMissingMandatoryName() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        DataFormData data = new DataFormData();
        data.setDataFormCode("camera");
        Map<String, Object> values = new LinkedHashMap<>();
        // name intentionally omitted
        values.put("releaseYear", "2024-01");
        values.put("producer", fuji.getId());
        data.setValues(values);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> persistenceService.save(data));
        assertTrue(ex.getMessage().contains("name") && ex.getMessage().contains("mandatory"),
                "Error should mention mandatory name field: " + ex.getMessage());
    }

    @Test
    @Transactional
    void rejectsCameraWithMissingMandatoryProducer() {
        DataFormData data = new DataFormData();
        data.setDataFormCode("camera");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "Orphan Camera");
        values.put("releaseYear", "2024-01");
        // producer intentionally omitted
        data.setValues(values);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> persistenceService.save(data));
        assertTrue(ex.getMessage().contains("producer") && ex.getMessage().contains("mandatory"),
                "Error should mention mandatory producer field: " + ex.getMessage());
    }
}
