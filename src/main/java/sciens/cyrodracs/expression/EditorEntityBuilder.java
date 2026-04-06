package sciens.cyrodracs.expression;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

@Component
public class EditorEntityBuilder {

    private final EntityManager entityManager;

    public EditorEntityBuilder(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Build a transient entity from form state.
     * Scalar fields: set from formState values, type-converted.
     * @ManyToOne fields: formState contains the related entity's ID;
     * the related entity is loaded from DB via entityManager.find().
     */
    public <T> T buildFromFormState(Class<T> entityClass, Long entityId,
                                     Map<String, String> formState) {
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();

            if (entityId != null) {
                setProperty(entity, "id", entityId);
            }

            EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);

            for (var attr : entityType.getSingularAttributes()) {
                String attrName = attr.getName();
                if ("id".equals(attrName)) continue;

                String formValue = formState.get(attrName);
                if (formValue == null || formValue.isEmpty()) continue;

                if (attr.isAssociation()) {
                    Class<?> relatedClass = attr.getJavaType();
                    Long relatedId = Long.valueOf(formValue);
                    Object related = entityManager.find(relatedClass, relatedId);
                    setProperty(entity, attrName, related);
                } else {
                    Object converted = convertValue(formValue, attr.getJavaType());
                    setProperty(entity, attrName, converted);
                }
            }

            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to build entity from form state", e);
        }
    }

    private void setProperty(Object entity, String name, Object value) {
        try {
            String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method m : entity.getClass().getMethods()) {
                if (m.getName().equals(setter) && m.getParameterCount() == 1) {
                    m.invoke(entity, value);
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set property " + name, e);
        }
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == String.class) return value;
        if (targetType == Long.class || targetType == long.class) return Long.valueOf(value);
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(value);
        if (targetType == Double.class || targetType == double.class) return Double.valueOf(value);
        if (targetType == Float.class || targetType == float.class) return Float.valueOf(value);
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(value);
        if (targetType == YearMonth.class) {
            if (value.length() > 7 && value.charAt(4) == '-' && value.charAt(7) == '-') {
                return YearMonth.parse(value.substring(0, 7));
            }
            return YearMonth.parse(value);
        }
        if (targetType == LocalDate.class) return LocalDate.parse(value);
        if (targetType == LocalDateTime.class) return LocalDateTime.parse(value);
        return value;
    }
}
