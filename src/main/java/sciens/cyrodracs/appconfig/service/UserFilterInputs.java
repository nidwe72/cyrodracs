package sciens.cyrodracs.appconfig.service;

import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.FilterOperator;
import sciens.cyrodracs.appconfig.SortDirection;
import sciens.cyrodracs.appconfig.SortField;

import java.util.List;
import java.util.Map;

/**
 * Converts user-supplied FilterNodeInput / SortFieldInput maps from the
 * GraphQL layer into the domain FilterNode / SortField types consumed by
 * FilterExecutor. The input shape deliberately omits expressionRef so that
 * code authoring stays admin-only.
 */
public final class UserFilterInputs {

    private UserFilterInputs() {}

    public static FilterNode toFilterNode(Map<String, Object> input) {
        if (input == null) return null;
        FilterNode node = new FilterNode();

        Object typeStr = input.get("type");
        if (typeStr == null) {
            throw new IllegalArgumentException("userFilter node missing 'type'");
        }
        node.setType(FilterNodeType.valueOf(typeStr.toString()));

        Object field = input.get("field");
        if (field != null) node.setField(field.toString());

        Object op = input.get("operator");
        if (op != null) node.setOperator(FilterOperator.valueOf(op.toString()));

        Object value = input.get("value");
        if (value != null) node.setValue(value.toString());

        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) input.get("values");
        if (values != null) {
            node.setValues(values.stream().map(v -> v == null ? null : v.toString()).toList());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) input.get("children");
        if (children != null) {
            node.setChildren(children.stream().map(UserFilterInputs::toFilterNode).toList());
        }

        validate(node);
        return node;
    }

    public static List<SortField> toSortFields(List<Map<String, Object>> input) {
        if (input == null || input.isEmpty()) return List.of();
        return input.stream().map(m -> {
            Object field = m.get("field");
            Object direction = m.get("direction");
            if (field == null || direction == null) {
                throw new IllegalArgumentException(
                        "userSort entry requires non-null field and direction");
            }
            SortField sf = new SortField();
            sf.setField(field.toString());
            sf.setDirection(SortDirection.valueOf(direction.toString()));
            return sf;
        }).toList();
    }

    private static void validate(FilterNode node) {
        if (node.getType() == FilterNodeType.COMPARISON) {
            if (node.getField() == null || node.getOperator() == null) {
                throw new IllegalArgumentException(
                        "userFilter COMPARISON requires non-null field and operator");
            }
        }
    }
}
