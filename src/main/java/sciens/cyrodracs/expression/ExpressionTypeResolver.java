package sciens.cyrodracs.expression;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.springframework.stereotype.Component;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Resolves variable types and method signatures from injectable source code
 * using JavaParser (AST) and Java reflection.
 */
@Component
public class ExpressionTypeResolver {

    /** Import prefixes matching InjectableSourceBuilder scaffold. */
    private static final String[] IMPORT_PREFIXES = {
            "sciens.cyrodracs.expression.",
            "sciens.cyrodracs.appconfig.",
            "sciens.cyrodracs.camera.",
            "java.util.",
            "java.time.",
            "java.lang.",
    };

    public record MethodInfo(String name, String returnType, boolean returnsResolvable) {}

    public record TypeContext(
            Map<String, String> variables,
            Map<String, List<MethodInfo>> methods
    ) {}

    /**
     * Analyzes the expression source and returns type context for autocomplete.
     */
    public TypeContext resolve(ExpressionType type, InjectableBaseClass baseClass,
                               String expression) {
        String fullSource = type == ExpressionType.INJECTABLE_CLASS
                ? InjectableSourceBuilder.buildClassSource("$Resolve", baseClass, expression)
                : InjectableSourceBuilder.buildSnippetSource("$Resolve", baseClass, expression);

        Map<String, String> variables = extractVariableTypes(fullSource);
        Map<String, List<MethodInfo>> methods = buildMethodMap(variables);
        return new TypeContext(variables, methods);
    }

    /**
     * Parses source with JavaParser, extracts variable declarations and method
     * parameter types. Returns a map of variable/parameter name → simple type name.
     */
    private Map<String, String> extractVariableTypes(String source) {
        Map<String, String> variables = new LinkedHashMap<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(source);

            // Extract local variable declarations
            cu.findAll(VariableDeclarator.class).forEach(vd -> {
                String typeName = vd.getType().asString();
                String varName = vd.getNameAsString();
                variables.put(varName, typeName);
            });

            // Extract method parameters
            cu.findAll(Parameter.class).forEach(param -> {
                String typeName = param.getType().asString();
                String paramName = param.getNameAsString();
                variables.put(paramName, typeName);
            });

            // Extract user-defined method return types (for chaining helper methods)
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                String methodName = md.getNameAsString();
                String returnType = md.getType().asString();
                // Store as callable: when user types "this.methodName()" or just "methodName()"
                variables.put(methodName + "()", returnType);
            });

        } catch (ParseProblemException e) {
            // Partial parse — return whatever we got so far
        }
        return variables;
    }

    /**
     * Builds a method map for all types referenced in the variable map.
     * Transitively includes return types that are themselves resolvable.
     */
    private Map<String, List<MethodInfo>> buildMethodMap(Map<String, String> variables) {
        Map<String, List<MethodInfo>> methods = new LinkedHashMap<>();
        Set<String> toResolve = new LinkedHashSet<>(variables.values());
        Set<String> resolved = new HashSet<>();

        while (!toResolve.isEmpty()) {
            String typeName = toResolve.iterator().next();
            toResolve.remove(typeName);
            if (resolved.contains(typeName)) continue;
            resolved.add(typeName);

            Class<?> clazz = resolveClass(typeName);
            if (clazz == null) continue;

            List<MethodInfo> methodList = new ArrayList<>();
            for (Method m : clazz.getMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                if (m.getDeclaringClass() == Object.class) continue;
                if (m.getParameterCount() > 0 && !isCommonMethod(m)) continue;

                String returnTypeName = m.getReturnType().getSimpleName();
                boolean returnsResolvable = resolveClass(returnTypeName) != null;

                methodList.add(new MethodInfo(
                        formatMethodName(m),
                        returnTypeName,
                        returnsResolvable
                ));

                // Transitively resolve return types for chaining
                if (returnsResolvable && !resolved.contains(returnTypeName)) {
                    toResolve.add(returnTypeName);
                }
            }

            // Sort: getters first, then others
            methodList.sort(Comparator.comparing(MethodInfo::name));
            methods.put(typeName, methodList);
        }

        return methods;
    }

    /**
     * Resolves a simple class name to a Class using the import prefixes
     * from the injectable scaffold.
     */
    Class<?> resolveClass(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return null;
        // Handle primitive and void types
        if (simpleName.equals("void") || simpleName.equals("int") || simpleName.equals("long")
                || simpleName.equals("boolean") || simpleName.equals("double")
                || simpleName.equals("float") || simpleName.equals("char")
                || simpleName.equals("byte") || simpleName.equals("short")) {
            return null;
        }
        // Strip generic parameters (e.g., "List<String>" → "List")
        int genericIdx = simpleName.indexOf('<');
        String rawName = genericIdx > 0 ? simpleName.substring(0, genericIdx) : simpleName;

        for (String prefix : IMPORT_PREFIXES) {
            try {
                return Class.forName(prefix + rawName);
            } catch (ClassNotFoundException e) {
                // try next prefix
            }
        }
        return null;
    }

    private String formatMethodName(Method m) {
        if (m.getParameterCount() == 0) {
            return m.getName() + "()";
        }
        StringJoiner params = new StringJoiner(", ");
        for (var param : m.getParameters()) {
            params.add(param.getType().getSimpleName() + " " + param.getName());
        }
        return m.getName() + "(" + params + ")";
    }

    private boolean isCommonMethod(Method m) {
        // Include methods with parameters that are commonly useful
        return m.getName().equals("equals") || m.getName().equals("compareTo")
                || m.getName().equals("get") || m.getName().equals("put")
                || m.getName().equals("contains") || m.getName().equals("containsKey")
                || m.getName().equals("add") || m.getName().equals("of")
                || m.getName().equals("valueOf");
    }
}
