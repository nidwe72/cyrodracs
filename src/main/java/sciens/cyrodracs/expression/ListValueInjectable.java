package sciens.cyrodracs.expression;

import java.util.ArrayList;
import java.util.List;

public abstract class ListValueInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private List<String> result = new ArrayList<>();

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(List<?> values) {
        this.result = values == null ? List.of()
            : values.stream().map(String::valueOf).toList();
    }

    public final List<String> getResult() { return result; }
}
