package sciens.cyrodracs.expression;

public abstract class ScalarValueInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private String result;

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(Object value) {
        this.result = value == null ? null : String.valueOf(value);
    }

    public final String getResult() { return result; }
}
