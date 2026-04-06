package sciens.cyrodracs.expression;

public abstract class BooleanInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private Boolean result;

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(boolean value) {
        this.result = value;
    }

    public final Boolean getResult() { return result; }
}
