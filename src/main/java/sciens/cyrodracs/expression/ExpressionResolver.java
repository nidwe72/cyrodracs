package sciens.cyrodracs.expression;

import org.springframework.stereotype.Component;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.Expression;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.InjectableBaseClass;

@Component
public class ExpressionResolver {

    private final AppConfigStore appConfigStore;
    private final InjectableExecutor injectableExecutor;

    public ExpressionResolver(AppConfigStore appConfigStore, InjectableExecutor injectableExecutor) {
        this.appConfigStore = appConfigStore;
        this.injectableExecutor = injectableExecutor;
    }

    /**
     * Resolve an expression to a scalar String value.
     */
    public String resolve(String expressionCode, ExpressionContext context) {
        Expression expr = getExpression(expressionCode);

        return switch (expr.getType()) {
            case STATIC             -> expr.getExpression();
            case CONTEXT_PATH       -> resolveContextPath(expr.getExpression(), context);
            case SPEL               -> throw new UnsupportedOperationException("SPEL not yet implemented");
            case INJECTABLE_SNIPPET -> injectableExecutor.executeSnippet(expr, context);
            case INJECTABLE_CLASS   -> injectableExecutor.executeClass(expr, context);
        };
    }

    /**
     * Resolve an expression to a FilterNode tree.
     * The referenced expression MUST have baseClass: FILTER.
     */
    public FilterNode resolveFilter(String expressionCode, ExpressionContext context) {
        Expression expr = getExpression(expressionCode);
        if (expr.getBaseClass() != InjectableBaseClass.FILTER) {
            throw new IllegalArgumentException(
                "Expression '" + expressionCode + "' has baseClass " + expr.getBaseClass()
                + " but FILTER is required for filterInjectableRef");
        }
        return injectableExecutor.executeFilter(expr, context);
    }

    private Expression getExpression(String expressionCode) {
        Expression expr = appConfigStore.getAppConfig().getExpressions().get(expressionCode);
        if (expr == null) {
            throw new IllegalArgumentException("Unknown expression: " + expressionCode);
        }
        return expr;
    }

    private String resolveContextPath(String path, ExpressionContext context) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            Object val = context.get(path);
            return val == null ? null : String.valueOf(val);
        }
        String contextName = path.substring(0, dot);
        String propertyPath = path.substring(dot + 1);
        Object contextObj = context.get(contextName);
        if (contextObj == null) return null;

        // Walk dot-path via reflection
        Object current = contextObj;
        for (String segment : propertyPath.split("\\.")) {
            if (current == null) return null;
            current = getProperty(current, segment);
        }
        return current == null ? null : String.valueOf(current);
    }

    private Object getProperty(Object obj, String name) {
        try {
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return obj.getClass().getMethod(getter).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
