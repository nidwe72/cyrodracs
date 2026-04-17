package sciens.cyrodracs.expression;

import org.junit.jupiter.api.Test;
import sciens.cyrodracs.appconfig.Expression;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BooleanInjectableExecutorTest {

    private final InjectableExecutor executor = new InjectableExecutor();

    @Test
    void snippetReturnsTrue() {
        Expression expr = booleanSnippet("alwaysTrue", "setResult(true);");
        ExpressionContext ctx = emptyContext();

        Boolean result = executor.executeBoolean(expr, ctx);

        assertTrue(result);
    }

    @Test
    void snippetReturnsFalse() {
        Expression expr = booleanSnippet("alwaysFalse", "setResult(false);");
        ExpressionContext ctx = emptyContext();

        Boolean result = executor.executeBoolean(expr, ctx);

        assertFalse(result);
    }

    @Test
    void snippetReadsFormState() {
        Expression expr = booleanSnippet("checkProducer",
                "String val = getInjectionContext().getFormValue(\"producer\");\n"
              + "setResult(val != null && !val.isEmpty());");

        ExpressionContext withProducer = contextWithFormState(Map.of("producer", "5"));
        assertTrue(executor.executeBoolean(expr, withProducer));

        ExpressionContext withoutProducer = contextWithFormState(Map.of());
        assertFalse(executor.executeBoolean(expr, withoutProducer));
    }

    @Test
    void throwsWhenNotBooleanInjectable() {
        Expression expr = new Expression();
        expr.setCode("notBoolean");
        expr.setType(ExpressionType.INJECTABLE_SNIPPET);
        expr.setBaseClass(InjectableBaseClass.SCALAR_VALUE);
        expr.setExpression("setResult(\"hello\");");

        ExpressionContext ctx = emptyContext();

        assertThrows(IllegalStateException.class,
                () -> executor.executeBoolean(expr, ctx));
    }

    private Expression booleanSnippet(String code, String body) {
        Expression expr = new Expression();
        expr.setCode(code);
        expr.setType(ExpressionType.INJECTABLE_SNIPPET);
        expr.setBaseClass(InjectableBaseClass.BOOLEAN_VALUE);
        expr.setExpression(body);
        return expr;
    }

    private ExpressionContext emptyContext() {
        return contextWithFormState(Map.of());
    }

    private ExpressionContext contextWithFormState(Map<String, String> formState) {
        ExpressionContext ctx = new ExpressionContext();
        ctx.put("formState", formState);
        return ctx;
    }
}
