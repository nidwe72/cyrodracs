package sciens.cyrodracs.appconfig;

public enum InjectableBaseClass {

    SCALAR_VALUE("sciens.cyrodracs.expression.ScalarValueInjectable"),
    BOOLEAN_VALUE("sciens.cyrodracs.expression.BooleanInjectable"),
    LIST_VALUE("sciens.cyrodracs.expression.ListValueInjectable"),
    FILTER("sciens.cyrodracs.expression.FilterInjectable");

    private final String fqcn;

    InjectableBaseClass(String fqcn) { this.fqcn = fqcn; }
    public String getFqcn() { return fqcn; }
}
