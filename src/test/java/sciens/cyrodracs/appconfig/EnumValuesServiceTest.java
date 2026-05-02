package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.EnumValuesService;
import sciens.cyrodracs.camera.CameraProducer;
import sciens.cyrodracs.camera.CameraProducerRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@link EnumValuesService} per
 * columnFilters.md CF3.4.5 + CF3.4.6.
 *
 * <p>Asserts against the cameras ENTITY_LIST ViewNode and the
 * {@code photoEquipmentMarketSegment} column. Seed data per G.3:
 * <ul>
 *   <li>Hasselblad → PROFESSIONAL only (4 cameras)</li>
 *   <li>Polaroid → ENTRY_LEVEL only (4 cameras)</li>
 *   <li>Leica → PROSUMER + PROFESSIONAL (4 cameras)</li>
 *   <li>Fuji → all four (6 cameras)</li>
 *   <li>Nikon → all four (21 backfilled cameras)</li>
 *   <li>(Mamiya / Ernemann / Pentax / etc. cameras have NULL segment
 *       and don't contribute distinct values)</li>
 * </ul>
 */
@SpringBootTest
class EnumValuesServiceTest {

    @Autowired EnumValuesService service;
    @Autowired AppConfigStore appConfigStore;
    @Autowired CameraProducerRepository producerRepo;

    @BeforeEach
    void reloadConfig() {
        appConfigStore.reload();
    }

    /**
     * CF3.4.5 — no filter → DISTINCT across all seeded cameras yields all
     * four declared constants (Nikon + Fuji each cover all four).
     */
    @Test
    @Transactional(readOnly = true)
    void noFilter_returnsAllFourSegmentsPresentInData() {
        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                null, null, null);

        assertEquals(
                List.of("ENTRY_LEVEL", "ENTHUSIAST", "PROSUMER", "PROFESSIONAL"),
                result.getValues(),
                "All four segments present in data, returned in declaration order");
    }

    /**
     * CF3.4.5 canonical asymmetry case — filter producer = Polaroid,
     * dropdown collapses to ENTRY_LEVEL only.
     */
    @Test
    @Transactional(readOnly = true)
    void polaroidProducerFilter_collapsesToEntryLevelOnly() {
        CameraProducer polaroid = producerRepo.findByName("Polaroid").orElseThrow();
        FilterNode userFilter = comparison("producer.id", String.valueOf(polaroid.getId()));

        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                userFilter, null, null);

        assertEquals(List.of("ENTRY_LEVEL"), result.getValues());
    }

    /**
     * CF3.4.5 — filter producer = Hasselblad, dropdown collapses to
     * PROFESSIONAL only.
     */
    @Test
    @Transactional(readOnly = true)
    void hasselbladProducerFilter_collapsesToProfessionalOnly() {
        CameraProducer hasselblad = producerRepo.findByName("Hasselblad").orElseThrow();
        FilterNode userFilter = comparison("producer.id", String.valueOf(hasselblad.getId()));

        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                userFilter, null, null);

        assertEquals(List.of("PROFESSIONAL"), result.getValues());
    }

    /**
     * CF3.4.5 — filter producer = Leica, dropdown shows two segments
     * (PROSUMER + PROFESSIONAL), in declaration order.
     */
    @Test
    @Transactional(readOnly = true)
    void leicaProducerFilter_showsProsumerAndProfessional() {
        CameraProducer leica = producerRepo.findByName("Leica").orElseThrow();
        FilterNode userFilter = comparison("producer.id", String.valueOf(leica.getId()));

        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                userFilter, null, null);

        assertEquals(List.of("PROSUMER", "PROFESSIONAL"), result.getValues());
    }

    /**
     * CF3.4.6 — pending row enum values are unioned into the result
     * even when committed rows don't contain them. Simulated here with
     * a producer whose committed cameras only have ENTRY_LEVEL (Polaroid):
     * pending augments with PROFESSIONAL, result includes both.
     */
    @Test
    @Transactional(readOnly = true)
    void pendingValues_unionedIntoRestrictedResult() {
        CameraProducer polaroid = producerRepo.findByName("Polaroid").orElseThrow();
        FilterNode userFilter = comparison("producer.id", String.valueOf(polaroid.getId()));
        Map<String, List<String>> pending = Map.of(
                "photoEquipmentMarketSegment", List.of("PROFESSIONAL"));

        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                userFilter, null, pending);

        // ENTRY_LEVEL from committed Polaroid cameras + PROFESSIONAL from pending,
        // both in declaration order.
        assertEquals(List.of("ENTRY_LEVEL", "PROFESSIONAL"), result.getValues());
    }

    /**
     * CF3.4.6 *Edge cases* — pending value not in declared enum
     * constants is silently dropped from the union (defensive against
     * stale frontend / version drift).
     */
    @Test
    @Transactional(readOnly = true)
    void pendingValueNotInDeclaredEnum_dropped() {
        CameraProducer polaroid = producerRepo.findByName("Polaroid").orElseThrow();
        FilterNode userFilter = comparison("producer.id", String.valueOf(polaroid.getId()));
        Map<String, List<String>> pending = Map.of(
                "photoEquipmentMarketSegment",
                List.of("PROFESSIONAL", "BOGUS_CONSTANT_NOT_IN_ENUM"));

        var result = service.getValues(
                "cameras", null, null,
                "photoEquipmentMarketSegment",
                userFilter, null, pending);

        // BOGUS_CONSTANT silently dropped; PROFESSIONAL kept.
        assertEquals(List.of("ENTRY_LEVEL", "PROFESSIONAL"), result.getValues());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static FilterNode comparison(String field, String value) {
        FilterNode n = new FilterNode();
        n.setType(FilterNodeType.COMPARISON);
        n.setField(field);
        n.setOperator(FilterOperator.EQUALS);
        n.setValue(value);
        return n;
    }
}
