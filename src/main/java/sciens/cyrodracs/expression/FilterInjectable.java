package sciens.cyrodracs.expression;

import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.FilterOperator;

import java.util.List;

public abstract class FilterInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private FilterNode result;

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(FilterNode filterNode) {
        this.result = filterNode;
    }

    protected FilterNode comparison(String field, FilterOperator operator, Object value) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value == null ? null : String.valueOf(value));
        return node;
    }

    protected FilterNode and(FilterNode... children) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.AND_GROUP);
        node.setChildren(List.of(children));
        return node;
    }

    protected FilterNode or(FilterNode... children) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.OR_GROUP);
        node.setChildren(List.of(children));
        return node;
    }

    protected FilterNode in(String field, List<?> values) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IN);
        node.setValues(values.stream().map(String::valueOf).toList());
        return node;
    }

    protected FilterNode isNull(String field) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IS_NULL);
        return node;
    }

    protected FilterNode isNotNull(String field) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IS_NOT_NULL);
        return node;
    }

    public final FilterNode getResult() { return result; }
}
