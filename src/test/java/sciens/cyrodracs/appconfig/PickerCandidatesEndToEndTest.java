package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.service.PickerCandidatesService;
import sciens.cyrodracs.camera.CameraLensMount;
import sciens.cyrodracs.camera.CameraLensMountRepository;
import sciens.cyrodracs.camera.CameraProducer;
import sciens.cyrodracs.camera.CameraProducerRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the column-filter picker endpoint
 * ({@link PickerCandidatesService#getCandidates}).
 *
 * <p>Phase 2 scope: protocol + validation. The CF3.4.3 algorithm itself lands
 * in Phase 3; baseline tests here verify the existing CF3.4.1-only behaviour
 * still holds with the new request shape, and that malformed requests are
 * rejected loudly per the spec's <em>Edge cases</em> section.
 *
 * <p>Prerequisites (seeded by sibling tests in the same suite):
 * <ul>
 *   <li>{@code FujiLensMountInsertTest} — producers + mounts + mappings</li>
 *   <li>{@code GridConfigSeederTest} — lensMountMappings GRID with the
 *       Inventor column (key {@code cameraLensMount.producer}) and
 *       producerCaption.searchFields</li>
 * </ul>
 */
@SpringBootTest
class PickerCandidatesEndToEndTest {

    @Autowired PickerCandidatesService pickerService;
    @Autowired AppConfigStore appConfigStore;
    @Autowired CameraProducerRepository producerRepo;
    @Autowired CameraLensMountRepository mountRepo;

    @BeforeEach
    void reloadConfig() {
        appConfigStore.reload();
    }

    /** I11 — pickerColumnKey doesn't exist on the surface → IllegalArgumentException. */
    @Test
    @Transactional(readOnly = true)
    void rejectsUnknownColumnKey() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                pickerService.getCandidates(
                        null, "cameraProducer", "lensMountMappings",
                        "doesNotExist", null,
                        0, 20,
                        null, fuji.getId()));

        assertTrue(ex.getMessage().contains("doesNotExist"),
                "Error should name the offending column: " + ex.getMessage());
    }

    /**
     * I12 — pickerColumnKey resolves to a non-ENTITY_REF column → reject loudly.
     * Per CF3.4.3 <em>Edge cases</em>: pickers exist only for ENTITY_REF columns
     * (CF1.2). The frontend reads the column's filterType from
     * columnFilterMetadata and MUST NOT issue a picker request for any other
     * type. A failure here signals a client-side defect — fail visibly, do not
     * silently degrade.
     *
     * <p>The {@code lensMountMappings} GRID has no STRING column, so the test
     * targets a hypothetical malformed key that would resolve via JPA metamodel
     * to a non-association attribute. We use {@code id}, which is a plain
     * {@code Long} on every entity — not a MANY_TO_ONE / ONE_TO_ONE.
     */
    @Test
    @Transactional(readOnly = true)
    void rejectsNonEntityRefColumnKey() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                pickerService.getCandidates(
                        null, "cameraProducer", "lensMountMappings",
                        "id", null,
                        0, 20,
                        null, fuji.getId()));

        assertTrue(ex.getMessage().toLowerCase().contains("entity reference")
                        || ex.getMessage().toLowerCase().contains("not found"),
                "Error should explain non-ENTITY_REF rejection or column-lookup failure: "
                        + ex.getMessage());
    }

    /**
     * I1 — Canonical CF3.4.3 worked example. Editing Fuji, no other filters,
     * Inventor picker returns the 2 distinct inventors visible in the GRID:
     * ZeissIkon (invented M42) and Fuji (invented X-Mount).
     */
    @Test
    @Transactional(readOnly = true)
    void inventorPickerOnFujiReturnsZeissIkonAndFuji() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", null,
                0, 20,
                null, fuji.getId());

        assertEquals(2, result.getTotalCount(),
                "CF3.4.3 should restrict the Inventor picker to {ZeissIkon, Fuji}");
        Set<String> labels = result.getItems().stream()
                .map(PickerCandidate::getLabel)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ZeissIkon", "Fuji"), labels);
    }

    /**
     * I9 — TDD anchor. Inventor picker with cameraLensMount=M42 set on a
     * different column. The inner DISTINCT runs with both clauses
     * (cameraProducer.id=Fuji.id AND cameraLensMount.id=M42.id), narrowing
     * to the single row whose inventor is ZeissIkon (M42's creator).
     */
    @Test
    @Transactional(readOnly = true)
    void inventorPickerWithCameraLensMountM42_narrowsToZeissIkonOnly() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();
        CameraLensMount m42 = mountRepo.findAll().stream()
                .filter(m -> "M42".equals(m.getName()))
                .findFirst().orElseThrow();

        // user has set cameraLensMount = M42 — wire shape is cameraLensMount.id
        // because the column is an ENTITY_REF (the frontend compares by id).
        FilterNode otherFilter = new FilterNode();
        otherFilter.setType(FilterNodeType.COMPARISON);
        otherFilter.setField("cameraLensMount.id");
        otherFilter.setOperator(FilterOperator.EQUALS);
        otherFilter.setValue(m42.getId().toString());
        FilterNode userFilter = new FilterNode();
        userFilter.setType(FilterNodeType.AND_GROUP);
        userFilter.setChildren(List.of(otherFilter));

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", null,
                0, 20,
                userFilter, fuji.getId());

        assertEquals(1, result.getTotalCount(),
                "CF3.4.3 inner DISTINCT should respect otherUserFilters: M42's only inventor is ZeissIkon");
        assertEquals("ZeissIkon", result.getItems().get(0).getLabel());
    }

    /**
     * I2 — Same picker, user has already selected ZeissIkon. Excel-autofilter
     * convention: own filter is excluded from the inner DISTINCT, so the user
     * can switch to Fuji.
     */
    @Test
    @Transactional(readOnly = true)
    void inventorPickerWithOwnFilterSet_stillReturnsBothCandidates() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();
        CameraProducer zeissIkon = producerRepo.findByName("ZeissIkon").orElseThrow();

        // picker's own column is "cameraLensMount.producer"; an ENTITY_REF, so the
        // wire-level field is "cameraLensMount.producer.id". The strip helper's
        // prefix rule treats this as the picker's own filter and drops it.
        FilterNode ownFilter = new FilterNode();
        ownFilter.setType(FilterNodeType.COMPARISON);
        ownFilter.setField("cameraLensMount.producer.id");
        ownFilter.setOperator(FilterOperator.EQUALS);
        ownFilter.setValue(zeissIkon.getId().toString());
        FilterNode userFilter = new FilterNode();
        userFilter.setType(FilterNodeType.AND_GROUP);
        userFilter.setChildren(List.of(ownFilter));

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", null,
                0, 20,
                userFilter, fuji.getId());

        assertEquals(2, result.getTotalCount(),
                "Picker's own filter MUST be stripped — user must be able to switch values");
    }

    /**
     * I3 — cameraLensMount picker on Fuji's GRID. 3 mounts in the system; only
     * 2 (M42, X-Mount) appear in Fuji's mappings (K-mount excluded).
     */
    @Test
    @Transactional(readOnly = true)
    void cameraLensMountPickerOnFujiReturnsM42AndXMount() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount", null,
                0, 20,
                null, fuji.getId());

        assertEquals(2, result.getTotalCount(),
                "CF3.4.3 should restrict cameraLensMount picker to mounts Fuji has adopted");
        Set<String> labels = result.getItems().stream()
                .map(PickerCandidate::getLabel)
                .collect(Collectors.toSet());
        assertEquals(Set.of("M42", "X-Mount"), labels);
    }

    /**
     * I4 — cameraProducer picker (degenerate / singleton). The GRID's filter
     * pins cameraProducer = Fuji, so the picker collapses to {Fuji}.
     * Accepted as correct, not a defect.
     */
    @Test
    @Transactional(readOnly = true)
    void cameraProducerPickerOnFujiCollapsesToFujiOnly() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraProducer", null,
                0, 20,
                null, fuji.getId());

        assertEquals(1, result.getTotalCount());
        assertEquals("Fuji", result.getItems().get(0).getLabel());
    }

    /**
     * I7 — Typeahead matches via name. With the canonical {ZeissIkon, Fuji}
     * candidate set, typing "Fuji" narrows to one match.
     */
    @Test
    @Transactional(readOnly = true)
    void typeaheadByName_narrowsToFuji() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", "Fuji",
                0, 20,
                null, fuji.getId());

        assertEquals(1, result.getTotalCount());
        assertEquals("Fuji", result.getItems().get(0).getLabel());
    }

    /**
     * Brand-new (transient) producer — Inventor picker must show zero
     * candidates because no CameraLensMount2CameraProducer rows can
     * possibly reference a producer that doesn't yet exist. Mirrors the
     * GRID's own zero-rows behaviour in create-new mode (see
     * {@code GridDataEndToEndTest#gridReturnsZeroRowsInCreateNewMode}).
     *
     * <p>Driven by {@code producerMountFilter} emitting a match-nothing
     * predicate (`cameraProducer.id IS NULL`) when the editor entity has
     * no id; CF3.4.3's inner DISTINCT then yields no candidate ids and
     * the outer SELECT's IN-clause matches nothing.
     */
    @Test
    @Transactional(readOnly = true)
    void inventorPickerInCreateNewMode_returnsZeroCandidates() {
        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", null,
                0, 20,
                null, null);   // no editor entity, no user filter

        assertEquals(0, result.getTotalCount(),
                "Create-new mode: no mappings exist yet, so picker MUST be empty");
        assertTrue(result.getItems().isEmpty());
    }

    /**
     * I8 — Typeahead matches via a non-displayed searchField. The
     * producerCaption template renders only "{{name}}", but searchFields
     * include foundationYear. Typing "1934" matches Fuji via foundationYear
     * even though the displayed label doesn't show the year — the
     * search/display decoupling promise of CF3.5.1.
     */
    @Test
    @Transactional(readOnly = true)
    void typeaheadByFoundationYear_matchesFujiViaNonDisplayedField() {
        CameraProducer fuji = producerRepo.findByName("Fuji").orElseThrow();

        var result = pickerService.getCandidates(
                null, "cameraProducer", "lensMountMappings",
                "cameraLensMount.producer", "1934",
                0, 20,
                null, fuji.getId());

        // Fuji's foundationYear is 1934 (per FujiLensMountInsertTest seed).
        // ZeissIkon's foundationYear is 1926. Only Fuji matches "1934".
        assertEquals(1, result.getTotalCount(),
                "searchFields[foundationYear] should match Fuji even though the template only shows name");
        assertEquals("Fuji", result.getItems().get(0).getLabel());
    }
}
