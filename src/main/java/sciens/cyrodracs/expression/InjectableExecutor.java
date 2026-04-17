package sciens.cyrodracs.expression;

import org.codehaus.janino.SimpleCompiler;
import org.springframework.stereotype.Component;
import sciens.cyrodracs.appconfig.Expression;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.InjectableBaseClass;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class InjectableExecutor {

    private final Map<String, Class<?>> compilationCache = new ConcurrentHashMap<>();

    public String executeClass(Expression expr, ExpressionContext rawContext) {
        Class<?> compiled = getOrCompile(expr, true);
        return executeAndExtractString(compiled, rawContext);
    }

    public String executeSnippet(Expression expr, ExpressionContext rawContext) {
        Class<?> compiled = getOrCompile(expr, false);
        return executeAndExtractString(compiled, rawContext);
    }

    public FilterNode executeFilter(Expression expr, ExpressionContext rawContext) {
        boolean isClassBody = (expr.getType() == ExpressionType.INJECTABLE_CLASS);
        Class<?> compiled = getOrCompile(expr, isClassBody);
        IInjectable injectable = instantiateAndExecute(compiled, rawContext);
        if (injectable instanceof FilterInjectable f) {
            return f.getResult();
        }
        throw new IllegalStateException(
            "Expression '" + expr.getCode() + "' is not a FilterInjectable");
    }

    public Boolean executeBoolean(Expression expr, ExpressionContext rawContext) {
        boolean isClassBody = (expr.getType() == ExpressionType.INJECTABLE_CLASS);
        Class<?> compiled = getOrCompile(expr, isClassBody);
        IInjectable injectable = instantiateAndExecute(compiled, rawContext);
        if (injectable instanceof BooleanInjectable b) {
            return b.getResult();
        }
        throw new IllegalStateException(
            "Expression '" + expr.getCode() + "' is not a BooleanInjectable");
    }

    public void invalidateCache() {
        compilationCache.clear();
    }

    public void invalidateCache(String expressionCode) {
        compilationCache.remove(expressionCode);
    }

    private Class<?> getOrCompile(Expression expr, boolean isClassBody) {
        return compilationCache.computeIfAbsent(expr.getCode(), code ->
            isClassBody
                ? compileClassBody(code, expr.getBaseClass(), expr.getExpression())
                : compileSnippetBody(code, expr.getBaseClass(), expr.getExpression())
        );
    }

    private Class<?> compileClassBody(String code, InjectableBaseClass baseClass, String classBody) {
        String className = "Injectable_" + sanitize(code);
        String source = InjectableSourceBuilder.buildClassSource(className, baseClass, classBody);
        return compileSource(className, source);
    }

    private Class<?> compileSnippetBody(String code, InjectableBaseClass baseClass, String methodBody) {
        String className = "Snippet_" + sanitize(code);
        String source = InjectableSourceBuilder.buildSnippetSource(className, baseClass, methodBody);
        return compileSource(className, source);
    }

    private Class<?> compileSource(String className, String source) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.setParentClassLoader(getClass().getClassLoader());
            compiler.cook(source);
            return compiler.getClassLoader().loadClass(
                "sciens.cyrodracs.expression.generated." + className);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to compile injectable '" + className + "': " + e.getMessage(), e);
        }
    }

    private IInjectable instantiateAndExecute(Class<?> compiled, ExpressionContext rawContext) {
        try {
            IInjectable injectable = (IInjectable) compiled
                .getDeclaredConstructor().newInstance();

            InjectionContext ctx = buildInjectionContext(rawContext);
            injectContext(injectable, ctx);

            executeWithTimeout(injectable);
            return injectable;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to execute injectable: " + compiled.getName(), e);
        }
    }

    private String executeAndExtractString(Class<?> compiled, ExpressionContext rawContext) {
        IInjectable injectable = instantiateAndExecute(compiled, rawContext);
        if (injectable instanceof ScalarValueInjectable s) return s.getResult();
        if (injectable instanceof BooleanInjectable b) return String.valueOf(b.getResult());
        if (injectable instanceof ListValueInjectable l) return String.join(",", l.getResult());
        throw new IllegalStateException("Cannot extract String from " + injectable.getClass());
    }

    private void injectContext(IInjectable injectable, InjectionContext ctx) {
        if (injectable instanceof ScalarValueInjectable s) s.setInjectionContext(ctx);
        else if (injectable instanceof BooleanInjectable b) b.setInjectionContext(ctx);
        else if (injectable instanceof ListValueInjectable l) l.setInjectionContext(ctx);
        else if (injectable instanceof FilterInjectable f) f.setInjectionContext(ctx);
    }

    @SuppressWarnings("unchecked")
    private InjectionContext buildInjectionContext(ExpressionContext rawContext) {
        Object editorEntity = rawContext.get("editor");
        Map<String, String> formState = (Map<String, String>) rawContext.get("formState");
        Map<String, String> routeParams = (Map<String, String>) rawContext.get("routeParams");
        Map<String, String> sessionData = (Map<String, String>) rawContext.get("sessionData");
        return new InjectionContextImpl(editorEntity, formState, routeParams, sessionData);
    }

    private void executeWithTimeout(IInjectable injectable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(injectable::execute);
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Injectable execution exceeded 100ms timeout");
        } catch (ExecutionException e) {
            throw new RuntimeException("Injectable execution failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Injectable execution interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private String sanitize(String code) {
        return code.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
