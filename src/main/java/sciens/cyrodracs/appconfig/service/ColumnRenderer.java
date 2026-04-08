package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared utility for resolving column values from JPA entities.
 * Supports dot-separated paths (e.g., "cameraLensMount.name") and
 * Mustache rendering via EntityRenderer templates.
 *
 * Used by both ViewDataService (ENTITY_LIST tables) and
 * GridDataService (GRID elements).
 */
@Component
public class ColumnRenderer {

    private final EntityManager entityManager;

    public ColumnRenderer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Resolves a value from an entity using a dot-separated path.
     * Each segment calls the corresponding getter.
     *
     * Examples:
     *   "name"                     → entity.getName()
     *   "cameraLensMount"          → entity.getCameraLensMount()
     *   "cameraLensMount.name"     → entity.getCameraLensMount().getName()
     *   "cameraLensMount.producer" → entity.getCameraLensMount().getProducer()
     */
    public Object resolveValue(Object entity, String dotPath) {
        String[] segments = dotPath.split("\\.");
        Object current = entity;
        for (String segment : segments) {
            if (current == null) return null;
            current = getProperty(current, segment);
        }
        return current;
    }

    /**
     * Resolves a column value and applies a Mustache template if the final
     * value is a JPA entity. Returns a display-ready Object.
     */
    public Object resolveAndRender(Object entity, String dotPath, Template rendererTemplate) {
        Object value = resolveValue(entity, dotPath);
        if (value == null) return null;

        if (isJpaEntity(value.getClass())) {
            if (rendererTemplate != null) {
                return rendererTemplate.execute(buildEntityContext(value));
            }
            return getId(value);
        }
        return value;
    }

    /**
     * Builds a Mustache context map from a JPA entity's basic attributes.
     */
    public Map<String, Object> buildEntityContext(Object entity) {
        Map<String, Object> context = new LinkedHashMap<>();
        EntityType<?> metamodel = entityManager.getMetamodel().entity(resolveEntityClass(entity));
        for (SingularAttribute<?, ?> attr : metamodel.getSingularAttributes()) {
            if ("id".equals(attr.getName())) continue;
            Object value = getProperty(entity, attr.getName());
            if (value == null) continue;
            if (isJpaEntity(value.getClass())) {
                // Nested relationship: build a sub-context for Mustache {{relation.field}}
                context.put(attr.getName(), buildEntityContext(value));
            } else {
                context.put(attr.getName(), value.toString());
            }
        }
        return context;
    }

    public Long getId(Object entity) {
        try {
            return (Long) entity.getClass().getMethod("getId").invoke(entity);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public boolean isJpaEntity(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    public Class<?> resolveEntityClass(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) return current;
            current = current.getSuperclass();
        }
        return entity.getClass();
    }

    private Object getProperty(Object entity, String fieldName) {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : entity.getClass().getMethods()) {
            if (method.getName().equals(getterName) && method.getParameterCount() == 0) {
                try {
                    return method.invoke(entity);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
