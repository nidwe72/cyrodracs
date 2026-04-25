package sciens.cyrodracs.appconfig.service;

import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a table's list filter onto a picker's candidate entity type so a
 * picker doesn't offer candidates that could never produce a visible row
 * (CF3.4.1).
 *
 * Rules:
 *   COMPARISON  — kept iff its field starts with `{columnKey}.`, with that
 *                  prefix stripped. Otherwise contributes no constraint.
 *   AND_GROUP   — keep projectable children, drop non-projectable ones.
 *                  Mixed groups become a safe over-approximation.
 *   OR_GROUP    — keep iff EVERY child projects; otherwise drop the whole
 *                  group (a non-projectable OR branch would permit anything).
 *
 * Returns null when the input contributes no constraint to the picker.
 * Janino-based filters (FilterNode.expressionRef) are not handled here — the
 * caller skips injectable filters in v1 (CF3.4.1 Janino paragraph).
 */
public final class FilterProjector {

    private FilterProjector() {}

    public static FilterNode project(FilterNode node, String columnKey) {
        if (node == null || node.getType() == null) return null;
        String prefix = columnKey + ".";
        return projectInternal(node, prefix);
    }

    private static FilterNode projectInternal(FilterNode node, String prefix) {
        if (node == null || node.getType() == null) return null;
        switch (node.getType()) {
            case COMPARISON:
                if (node.getField() != null && node.getField().startsWith(prefix)) {
                    FilterNode copy = copyComparison(node);
                    copy.setField(node.getField().substring(prefix.length()));
                    return copy;
                }
                return null;
            case AND_GROUP:
                List<FilterNode> kept = new ArrayList<>();
                for (FilterNode child : node.getChildren()) {
                    FilterNode projected = projectInternal(child, prefix);
                    if (projected != null) kept.add(projected);
                }
                if (kept.isEmpty()) return null;
                if (kept.size() == 1) return kept.get(0);
                return group(FilterNodeType.AND_GROUP, kept);
            case OR_GROUP:
                List<FilterNode> orKept = new ArrayList<>();
                for (FilterNode child : node.getChildren()) {
                    FilterNode projected = projectInternal(child, prefix);
                    if (projected == null) return null;
                    orKept.add(projected);
                }
                if (orKept.isEmpty()) return null;
                if (orKept.size() == 1) return orKept.get(0);
                return group(FilterNodeType.OR_GROUP, orKept);
            default:
                return null;
        }
    }

    private static FilterNode copyComparison(FilterNode source) {
        FilterNode copy = new FilterNode();
        copy.setType(FilterNodeType.COMPARISON);
        copy.setField(source.getField());
        copy.setOperator(source.getOperator());
        copy.setValue(source.getValue());
        copy.setValues(source.getValues());
        return copy;
    }

    private static FilterNode group(FilterNodeType kind, List<FilterNode> children) {
        FilterNode group = new FilterNode();
        group.setType(kind);
        group.setChildren(children);
        return group;
    }
}
