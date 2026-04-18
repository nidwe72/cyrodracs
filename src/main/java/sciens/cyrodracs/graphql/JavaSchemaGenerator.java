package sciens.cyrodracs.graphql;

import java.lang.reflect.*;
import java.time.YearMonth;
import java.util.*;

/**
 * Generates a GraphQL SDL schema string from Java classes via reflection.
 * <p>
 * Scans JavaBean getters (getX/isX), maps Java types to GraphQL types,
 * and respects {@link GraphQLInternal} for field blacklisting.
 */
public class JavaSchemaGenerator {

    private final Set<String> globalExclusions = new HashSet<>(List.of(
            "parentObjectId", "parentTypeId"
    ));
    private final List<String> globalSuffixExclusions = new ArrayList<>(List.of("NodeId"));
    private final Map<Class<?>, Set<String>> perClassExclusions = new HashMap<>();

    private final Map<Class<?>, String> registeredTypes = new LinkedHashMap<>();
    private final Set<Class<?>> enumTypes = new LinkedHashSet<>();
    private final Set<Class<?>> processed = new HashSet<>();
    private final Queue<Class<?>> toProcess = new ArrayDeque<>();

    /** Exclude specific fields on a specific class. */
    public void exclude(Class<?> clazz, String... fields) {
        perClassExclusions.computeIfAbsent(clazz, k -> new HashSet<>()).addAll(List.of(fields));
    }

    /**
     * Generates the complete SDL string for the given types.
     * Call {@link #addType} for each root type before calling this.
     */
    public String generateSDL(String querySDL, String mutationSDL, String inputSDL) {
        // Process all queued types (breadth-first discovery)
        while (!toProcess.isEmpty()) {
            processType(toProcess.poll());
        }

        var sb = new StringBuilder();

        // Custom scalars
        sb.append("scalar JSON\n");
        sb.append("scalar YearMonth\n\n");

        // Enums
        for (var enumClass : enumTypes) {
            sb.append(generateEnum(enumClass)).append("\n");
        }

        // Object types
        for (var entry : registeredTypes.entrySet()) {
            sb.append(entry.getValue()).append("\n");
        }

        // Query, Mutation, Input types (hand-written — these define the API surface)
        sb.append(querySDL).append("\n");
        sb.append(mutationSDL).append("\n");
        sb.append(inputSDL).append("\n");

        return sb.toString();
    }

    /** Register a type to be included in the schema. Nested types are discovered automatically. */
    public void addType(Class<?> clazz) {
        if (!processed.contains(clazz) && !isScalar(clazz) && !clazz.isEnum()) {
            toProcess.add(clazz);
        }
    }

    private void processType(Class<?> clazz) {
        if (processed.contains(clazz) || isScalar(clazz)) return;
        processed.add(clazz);

        if (clazz.isEnum()) {
            enumTypes.add(clazz);
            return;
        }

        var sb = new StringBuilder();
        sb.append("type ").append(typeName(clazz)).append(" {\n");

        for (var getter : getSortedGetters(clazz)) {
            String fieldName = getterToFieldName(getter);
            if (isExcluded(clazz, fieldName)) continue;

            String graphQLType = javaTypeToGraphQL(getter.getGenericReturnType());
            sb.append("  ").append(fieldName).append(": ").append(graphQLType).append("\n");
        }

        sb.append("}\n");
        registeredTypes.put(clazz, sb.toString());
    }

    private List<Method> getSortedGetters(Class<?> clazz) {
        // For records, use record component accessors (name(), line(), etc.)
        if (clazz.isRecord()) {
            return Arrays.stream(clazz.getRecordComponents())
                    .map(rc -> {
                        try { return rc.getAccessor(); }
                        catch (Exception e) { return null; }
                    })
                    .filter(m -> m != null)
                    .sorted(Comparator.comparing(Method::getName))
                    .toList();
        }

        // For regular classes, use JavaBean getter convention
        return Arrays.stream(clazz.getMethods())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> !m.getDeclaringClass().equals(Object.class))
                .filter(m -> m.getName().startsWith("get") || m.getName().startsWith("is"))
                .filter(m -> !m.getName().equals("getClass"))
                .filter(m -> m.getReturnType() != void.class)
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }

    private String getterToFieldName(Method getter) {
        String name = getter.getName();
        // Record accessors use the field name directly (e.g., line(), message())
        if (getter.getDeclaringClass().isRecord()) return name;
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return name;
    }

    private boolean isExcluded(Class<?> clazz, String fieldName) {
        if (globalExclusions.contains(fieldName)) return true;
        for (var suffix : globalSuffixExclusions) {
            if (fieldName.endsWith(suffix)) return true;
        }
        var classExcl = perClassExclusions.get(clazz);
        if (classExcl != null && classExcl.contains(fieldName)) return true;

        // Check @GraphQLInternal annotation on the field
        try {
            Field field = findDeclaredField(clazz, fieldName);
            if (field != null && field.isAnnotationPresent(GraphQLInternal.class)) return true;
        } catch (Exception ignored) {}

        return false;
    }

    private Field findDeclaredField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private String javaTypeToGraphQL(Type type) {
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();

            // List<X> → [X]
            if (List.class.isAssignableFrom(raw) || Collection.class.isAssignableFrom(raw)) {
                Type arg = pt.getActualTypeArguments()[0];
                return "[" + javaTypeToGraphQL(arg) + "]";
            }

            // Map<String, Object> → JSON
            if (Map.class.isAssignableFrom(raw)) {
                Type valueType = pt.getActualTypeArguments()[1];
                if (valueType == Object.class) {
                    return "JSON";
                }
                // Map<String, X> where X implements Coded → [X] (key is redundant)
                // The resolver handles the conversion; schema declares [X]
                return "[" + javaTypeToGraphQL(valueType) + "]";
            }
        }

        if (type instanceof Class<?> clazz) {
            // Primitives and boxed types
            if (clazz == String.class) return "String";
            if (clazz == int.class || clazz == Integer.class) return "Int";
            if (clazz == long.class || clazz == Long.class) return "Int";
            if (clazz == boolean.class || clazz == Boolean.class) return "Boolean";
            if (clazz == double.class || clazz == Double.class) return "Float";
            if (clazz == float.class || clazz == Float.class) return "Float";
            if (clazz == YearMonth.class) return "YearMonth";

            // Enums
            if (clazz.isEnum()) {
                enumTypes.add(clazz);
                return clazz.getSimpleName();
            }

            // Object types — queue for processing
            if (!processed.contains(clazz) && !toProcess.contains(clazz)) {
                toProcess.add(clazz);
            }
            return typeName(clazz);
        }

        // Fallback
        return "String";
    }

    private String typeName(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    private boolean isScalar(Class<?> clazz) {
        return clazz == String.class || clazz == int.class || clazz == Integer.class
                || clazz == long.class || clazz == Long.class
                || clazz == boolean.class || clazz == Boolean.class
                || clazz == double.class || clazz == Double.class
                || clazz == float.class || clazz == Float.class
                || clazz == YearMonth.class;
    }

    private String generateEnum(Class<?> enumClass) {
        var sb = new StringBuilder();
        sb.append("enum ").append(enumClass.getSimpleName()).append(" {\n");
        for (var constant : enumClass.getEnumConstants()) {
            sb.append("  ").append(((Enum<?>) constant).name()).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
