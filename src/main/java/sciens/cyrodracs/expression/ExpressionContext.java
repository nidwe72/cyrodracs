package sciens.cyrodracs.expression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries runtime context for expression evaluation.
 * Built per-request from ContextProviders.
 */
public class ExpressionContext {
    private final Map<String, Object> contexts = new LinkedHashMap<>();

    public void put(String name, Object contextObject) { contexts.put(name, contextObject); }
    public Object get(String name) { return contexts.get(name); }
    public Map<String, Object> getAll() { return Collections.unmodifiableMap(contexts); }
}
