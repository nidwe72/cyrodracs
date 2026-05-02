package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.PendingDotPathResolverService;
import sciens.cyrodracs.appconfig.service.PendingDotPathResolverService.DirectFieldValue;
import sciens.cyrodracs.appconfig.service.PendingDotPathResolverService.DotPathColumn;
import sciens.cyrodracs.camera.CameraLensMount;
import sciens.cyrodracs.camera.CameraLensMountRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@link PendingDotPathResolverService} per
 * gridElement.md G7.8.
 *
 * <p>Verifies the algorithm against the canonical
 * {@code lensMountMappings} GRID's row entity
 * ({@code CameraLensMount2CameraProducer}) — same domain as the
 * CF3.4.4 picker tests in {@code PickerCandidatesEndToEndTest}.</p>
 */
@SpringBootTest
class PendingDotPathResolverServiceTest {

    private static final String ROW_ENTITY_TYPE =
            "sciens.cyrodracs.camera.CameraLensMount2CameraProducer";

    @Autowired PendingDotPathResolverService service;
    @Autowired AppConfigStore appConfigStore;
    @Autowired CameraLensMountRepository mountRepo;

    @BeforeEach
    void reloadConfig() {
        appConfigStore.reload();
    }

    /**
     * G7.8 — direct column (no remainder) resolves the leaf id and renders
     * the display string. With rendererRef set, the picker's caption is
     * applied; leafId == directId.
     */
    @Test
    @Transactional(readOnly = true)
    void directColumn_returnsDirectIdAndRendersDisplay() {
        CameraLensMount m42 = mountRepo.findByName("M42").orElseThrow();

        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(new DirectFieldValue(0, "cameraLensMount", m42.getId())),
                List.of(new DotPathColumn("cameraLensMount", "lensMountCaption"))
        );

        assertEquals(1, results.size());
        var r = results.get(0);
        assertEquals(0, r.getPendingRowIndex());
        assertEquals("cameraLensMount", r.getColumnKey());
        assertEquals(m42.getId(), r.getLeafId(),
                "Direct column: leafId == directId (M42's id)");
        assertEquals("M42", r.getDisplayValue(),
                "Direct column: display rendered via lensMountCaption");
    }

    /**
     * G7.8 — single-segment dot-path (canonical CF3.4.4 example): the
     * Inventor column on lensMountMappings, key
     * {@code cameraLensMount.producer}. M42's inventor is ZeissIkon.
     */
    @Test
    @Transactional(readOnly = true)
    void singleSegmentDotPath_resolvesM42ToZeissIkon() {
        CameraLensMount m42 = mountRepo.findByName("M42").orElseThrow();

        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(new DirectFieldValue(0, "cameraLensMount", m42.getId())),
                List.of(new DotPathColumn("cameraLensMount.producer", "producerCaption"))
        );

        assertEquals(1, results.size());
        var r = results.get(0);
        assertEquals(0, r.getPendingRowIndex());
        assertEquals("cameraLensMount.producer", r.getColumnKey());
        assertNotNull(r.getLeafId(), "Leaf id must resolve to ZeissIkon's id");
        assertEquals("ZeissIkon", r.getDisplayValue(),
                "Display rendered via producerCaption — M42's inventor is ZeissIkon");
    }

    /**
     * G7.8 — null direct id yields null leafId and null displayValue.
     * Mirrors the create-new-mode parent-reference column case where
     * the parent entity has no id yet.
     */
    @Test
    @Transactional(readOnly = true)
    void nullDirectId_yieldsNullLeafAndNullDisplay() {
        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(new DirectFieldValue(0, "cameraProducer", null)),
                List.of(new DotPathColumn("cameraProducer", "producerCaption"))
        );

        assertEquals(1, results.size());
        var r = results.get(0);
        assertNull(r.getLeafId(), "Null direct id → null leafId");
        assertNull(r.getDisplayValue(), "Null direct id → null displayValue");
    }

    /**
     * G7.8 — when no direct value matches the column's first segment for a
     * pending row, the resolver still emits an entry for that
     * (pendingRowIndex, columnKey) tuple with null leafId / displayValue.
     * This is more diagnostic than omission: the frontend explicitly knows
     * "we tried this column for this row, no result" rather than guessing
     * from absence.
     */
    @Test
    @Transactional(readOnly = true)
    void missingFirstSegmentDirectValue_emitsNullResult() {
        // The pending row has a value for cameraProducer, but we ask the
        // service to resolve a column whose first segment is cameraLensMount.
        // Since there's no cameraLensMount entry for this row, the resolver
        // emits a null-result entry rather than omitting it.
        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(new DirectFieldValue(0, "cameraProducer", 5L)),
                List.of(new DotPathColumn("cameraLensMount.producer", "producerCaption"))
        );

        assertEquals(1, results.size(),
                "One entry per (pendingRowIndex, columnKey) tuple, even when unresolvable");
        var r = results.get(0);
        assertEquals(0, r.getPendingRowIndex());
        assertEquals("cameraLensMount.producer", r.getColumnKey());
        assertNull(r.getLeafId(), "Missing first-segment direct → null leafId");
        assertNull(r.getDisplayValue(), "Missing first-segment direct → null displayValue");
    }

    /**
     * G7.8 — batched: two pending rows × one dot-path column yields two
     * resolution entries, each tagged with its pendingRowIndex. Mirrors
     * the canonical Acme-with-2-pending-rows scenario.
     */
    @Test
    @Transactional(readOnly = true)
    void multiplePendingRows_singleDotPathColumn_batchedResolution() {
        CameraLensMount m42 = mountRepo.findByName("M42").orElseThrow();
        CameraLensMount xMount = mountRepo.findByName("X-Mount").orElseThrow();

        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(
                        new DirectFieldValue(0, "cameraLensMount", m42.getId()),
                        new DirectFieldValue(1, "cameraLensMount", xMount.getId())
                ),
                List.of(new DotPathColumn("cameraLensMount.producer", "producerCaption"))
        );

        assertEquals(2, results.size(),
                "Two pending rows × one dot-path column → two entries");

        var byIndex = new java.util.HashMap<Integer, ResolvedDotPathValue>();
        for (var r : results) byIndex.put(r.getPendingRowIndex(), r);

        assertEquals("ZeissIkon", byIndex.get(0).getDisplayValue(),
                "Row 0 → M42 → ZeissIkon");
        assertEquals("Fuji", byIndex.get(1).getDisplayValue(),
                "Row 1 → X-Mount → Fuji");
    }

    /**
     * G7.8 — null rendererRef yields null displayValue but still resolves
     * the leaf id (frontend uses leafId for filter equality even when no
     * display is needed).
     */
    @Test
    @Transactional(readOnly = true)
    void nullRendererRef_resolvesLeafIdButNoDisplay() {
        CameraLensMount m42 = mountRepo.findByName("M42").orElseThrow();

        var results = service.resolve(
                ROW_ENTITY_TYPE,
                List.of(new DirectFieldValue(0, "cameraLensMount", m42.getId())),
                List.of(new DotPathColumn("cameraLensMount.producer", null))
        );

        assertEquals(1, results.size());
        var r = results.get(0);
        assertNotNull(r.getLeafId(), "Leaf id must resolve even without rendererRef");
        assertNull(r.getDisplayValue(), "Null rendererRef → null displayValue");
    }
}
