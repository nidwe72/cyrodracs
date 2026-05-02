package sciens.cyrodracs.appconfig.service;

import org.junit.jupiter.api.Test;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.FilterOperator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PickerCandidatesService#stripPickerOwnFilter}. CF3.4.3
 * Step 1's "Why exclude the picker's own filter" rule — Excel-autofilter
 * convention: opening column K's picker must not include K's own filter in the
 * inner DISTINCT.
 *
 * <p>v1 frontend sends a single-level AND_GROUP per CF4.3, but the helper is
 * shape-agnostic and works on nested groups (forward-compat with v2).
 */
class PickerCandidatesStripFilterTest {

    @Test
    void nullFilterReturnsNull() {
        assertNull(PickerCandidatesService.stripPickerOwnFilter(null, "anything"));
    }

    @Test
    void singleComparisonOnPickerColumnIsRemoved() {
        FilterNode f = comparison("name", "Fuji");
        assertNull(PickerCandidatesService.stripPickerOwnFilter(f, "name"));
    }

    @Test
    void singleComparisonOnDifferentColumnIsKept() {
        FilterNode f = comparison("name", "Fuji");
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "producer.name");
        assertSame(f, result);
    }

    @Test
    void andGroupDropsMatchingLeafKeepsOthers() {
        FilterNode f = andGroup(
                comparison("cameraLensMount", "1"),    // own → drop
                comparison("releaseYear", "2020")       // other → keep
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertNotNull(result);
        // single survivor unwraps from AND_GROUP
        assertEquals(FilterNodeType.COMPARISON, result.getType());
        assertEquals("releaseYear", result.getField());
    }

    @Test
    void andGroupCollapsingToZeroSurvivorsReturnsNull() {
        FilterNode f = andGroup(
                comparison("cameraLensMount", "1"),
                comparison("cameraLensMount", "2")
        );
        assertNull(PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount"));
    }

    @Test
    void andGroupWithMultipleSurvivorsKeepsGroup() {
        FilterNode f = andGroup(
                comparison("cameraLensMount", "1"),    // own → drop
                comparison("releaseYear", "2020"),
                comparison("name", "Fuji")
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertEquals(FilterNodeType.AND_GROUP, result.getType());
        assertEquals(2, result.getChildren().size());
        assertEquals("releaseYear", result.getChildren().get(0).getField());
        assertEquals("name", result.getChildren().get(1).getField());
    }

    @Test
    void pickerColumnKeyNotPresentReturnsTreeUnchanged() {
        FilterNode f = andGroup(
                comparison("releaseYear", "2020"),
                comparison("name", "Fuji")
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertEquals(FilterNodeType.AND_GROUP, result.getType());
        assertEquals(2, result.getChildren().size());
    }

    @Test
    void orGroupRetainsGroupWrapperEvenWithSingleSurvivor() {
        // OR semantics differ from AND — a single-child OR_GROUP cannot be
        // safely unwrapped (could change semantics in nested contexts).
        FilterNode f = orGroup(
                comparison("cameraLensMount", "1"),    // own → drop
                comparison("releaseYear", "2020")
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertEquals(FilterNodeType.OR_GROUP, result.getType());
        assertEquals(1, result.getChildren().size());
    }

    @Test
    void dotPathColumnKeyMatchedExactly() {
        FilterNode f = andGroup(
                comparison("cameraLensMount.producer", "5"),    // own (Inventor) → drop
                comparison("cameraProducer", "4")                // other → keep
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount.producer");

        assertEquals(FilterNodeType.COMPARISON, result.getType());
        assertEquals("cameraProducer", result.getField());
    }

    @Test
    void entityRefSubPathIsTreatedAsOwnFilter() {
        // ENTITY_REF column "producer" — frontend sends user-filter as "producer.id = X"
        // because the comparison is by id, not by entity. The strip rule must treat
        // any sub-path of pickerColumnKey as the picker's own filter.
        FilterNode f = andGroup(
                comparison("producer.id", "7"),     // own column "producer" sub-path → drop
                comparison("releaseYear", "2020")    // other → keep
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "producer");

        assertEquals(FilterNodeType.COMPARISON, result.getType());
        assertEquals("releaseYear", result.getField());
    }

    @Test
    void siblingPathIsNotStripped() {
        // pickerColumnKey "cameraLensMount.producer" must NOT match a sibling path
        // "cameraLensMount.name" or the parent's other branch "cameraLensMount.id".
        FilterNode f = andGroup(
                comparison("cameraLensMount.id", "1"),         // sibling → keep
                comparison("cameraLensMount.producer", "5")    // own → drop
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount.producer");

        assertEquals(FilterNodeType.COMPARISON, result.getType());
        assertEquals("cameraLensMount.id", result.getField());
    }

    /**
     * CF3.4.3 *Stripping rule* — exact-equals OR exact `${K}.id` only,
     * NOT a general `startsWith(K + ".")` match.
     *
     * <p>Regression for the Lens Mount picker on the lensMountMappings
     * GRID when the user has filtered Inventor (key
     * {@code cameraLensMount.producer}, ENTITY_REF wire shape
     * {@code cameraLensMount.producer.id}). Before the fix the strip
     * incorrectly treated {@code cameraLensMount.producer.id} as a
     * sub-path of the picker column {@code cameraLensMount} and dropped
     * the Inventor filter, causing the Lens Mount picker to show all
     * mounts of the parent producer instead of only those visible under
     * the Inventor filter.</p>
     */
    @Test
    void deeperSubPathOfPickerKeyIsNotStripped_inventorFilterSurvivesLensMountPicker() {
        FilterNode f = andGroup(
                // Inventor filter — different column from "cameraLensMount",
                // shares the same path prefix. Must survive.
                comparison("cameraLensMount.producer.id", "4"),
                // Some other unrelated filter — must also survive.
                comparison("cameraProducer.id", "5")
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertNotNull(result);
        assertEquals(FilterNodeType.AND_GROUP, result.getType());
        assertEquals(2, result.getChildren().size(),
                "Both other-column filters must survive — neither IS the picker's own column");
    }

    /**
     * CF3.4.3 *Stripping rule* — the picker's own ENTITY_REF wire shape
     * {@code K + ".id"} is stripped exactly.
     */
    @Test
    void ownColumnEntityRefWireShapeIsStripped() {
        FilterNode f = andGroup(
                comparison("cameraLensMount.id", "1"),         // own (ENTITY_REF wire) → drop
                comparison("cameraLensMount.producer.id", "4") // Inventor → keep
        );
        FilterNode result = PickerCandidatesService.stripPickerOwnFilter(f, "cameraLensMount");

        assertEquals(FilterNodeType.COMPARISON, result.getType());
        assertEquals("cameraLensMount.producer.id", result.getField());
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

    private static FilterNode andGroup(FilterNode... children) {
        FilterNode n = new FilterNode();
        n.setType(FilterNodeType.AND_GROUP);
        n.setChildren(List.of(children));
        return n;
    }

    private static FilterNode orGroup(FilterNode... children) {
        FilterNode n = new FilterNode();
        n.setType(FilterNodeType.OR_GROUP);
        n.setChildren(List.of(children));
        return n;
    }
}
