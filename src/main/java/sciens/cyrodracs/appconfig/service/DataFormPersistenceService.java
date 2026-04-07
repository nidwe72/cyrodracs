package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormData;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.PendingChild;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.lang.reflect.Method;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataFormPersistenceService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;

    public DataFormPersistenceService(AppConfigStore appConfigStore, EntityManager entityManager) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
    }

    @Transactional
    public DataFormData saveWithChildren(DataFormData data) {
        DataFormData saved = save(data);
        Long parentId = saved.getEntityId();

        if (data.getPendingChildren() != null) {
            for (PendingChild child : data.getPendingChildren()) {
                DataFormData childData = new DataFormData();
                childData.setDataFormCode(child.getDataFormCode());
                childData.setValues(new java.util.LinkedHashMap<>(child.getValues()));
                // Inject parent ID into the context binding target field
                if (child.getContextBindingTarget() != null) {
                    childData.getValues().put(child.getContextBindingTarget(), parentId);
                }
                // Recursive: child may have its own pending children
                childData.setPendingChildren(child.getPendingChildren());
                saveWithChildren(childData);
            }
        }

        return saved;
    }

    @Transactional
    public DataFormData save(DataFormData data) {
        DataForm form = resolveDataForm(data.getDataFormCode());
        Class<?> entityClass = resolveEntityClass(form);

        Object entity;
        if (data.getEntityId() != null) {
            entity = entityManager.find(entityClass, data.getEntityId());
            if (entity == null) {
                throw new IllegalArgumentException("Entity not found: " + entityClass.getSimpleName()
                        + " id=" + data.getEntityId());
            }
        } else {
            try {
                entity = entityClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Cannot instantiate " + entityClass.getName(), e);
            }
        }

        applyValues(entity, form, data.getValues());
        checkUniqueConstraints(entity, entityClass, data.getEntityId());
        entity = entityManager.merge(entity);

        Long persistedId = getId(entity);
        data.setEntityId(persistedId);
        return data;
    }

    @Transactional(readOnly = true)
    public DataFormData load(String dataFormCode, Long entityId) {
        DataForm form = resolveDataForm(dataFormCode);
        Class<?> entityClass = resolveEntityClass(form);

        Object entity = entityManager.find(entityClass, entityId);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found: " + entityClass.getSimpleName()
                    + " id=" + entityId);
        }

        DataFormData data = new DataFormData();
        data.setDataFormCode(dataFormCode);
        data.setEntityId(entityId);

        for (var entry : form.getElements().entrySet()) {
            // Skip GRID elements — they don't bind to entity fields
            if (entry.getValue().getType() == DataFormElementType.GRID) continue;
            String fieldCode = entry.getKey();
            String bindingPath = resolveBindingPath(form, fieldCode);
            Object value = getProperty(entity, bindingPath);
            data.getValues().put(fieldCode, value);
        }
        return data;
    }

    private DataForm resolveDataForm(String dataFormCode) {
        AppConfig config = appConfigStore.getAppConfig();
        if (config == null) {
            throw new IllegalStateException("AppConfig not loaded");
        }
        DataForm form = config.getDataForms().get(dataFormCode);
        if (form == null) {
            throw new IllegalArgumentException("DataForm not found: " + dataFormCode);
        }
        return form;
    }

    private Class<?> resolveEntityClass(DataForm form) {
        if (form.getEntity() == null) {
            throw new IllegalStateException("DataForm '" + form.getCode() + "' has no entity type configured");
        }
        try {
            return Class.forName(form.getEntity().getFqcn());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Entity class not found: " + form.getEntity().getFqcn(), e);
        }
    }

    private void applyValues(Object entity, DataForm form, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            // Skip GRID elements — they don't bind to entity fields
            DataFormElement element = form.getElements().get(entry.getKey());
            if (element != null && element.getType() == DataFormElementType.GRID) continue;
            String bindingPath = resolveBindingPath(form, entry.getKey());
            setProperty(entity, bindingPath, entry.getValue());
        }
    }

    /**
     * Application-level unique constraint enforcement. Checks @UniqueConstraint annotations
     * on the entity class and queries the DB via JPQL to verify no duplicate exists.
     * Needed because SQLite with ddl-auto=update cannot add constraints to existing tables.
     */
    private void checkUniqueConstraints(Object entity, Class<?> entityClass, Long existingId) {
        Table tableAnn = entityClass.getAnnotation(Table.class);
        if (tableAnn == null) return;
        for (UniqueConstraint uc : tableAnn.uniqueConstraints()) {
            String[] columnNames = uc.columnNames();
            if (columnNames.length == 0) continue;

            List<String> fieldNames = new ArrayList<>();
            List<Object> paramValues = new ArrayList<>();
            boolean allResolved = true;

            for (String colName : columnNames) {
                String fieldName = mapColumnToField(entityClass, colName);
                if (fieldName == null) { allResolved = false; break; }

                Object value = getProperty(entity, fieldName);
                if (value == null) { allResolved = false; break; }

                // For @ManyToOne relationships, the getter returns the entity or its ID.
                // Extract the ID for comparison.
                boolean isRelationship = false;
                try {
                    java.lang.reflect.Field f = entityClass.getDeclaredField(fieldName);
                    isRelationship = f.isAnnotationPresent(jakarta.persistence.ManyToOne.class);
                } catch (NoSuchFieldException ignored) {}

                if (isRelationship) {
                    // value might be a JPA entity or a raw Long (depending on timing)
                    Object refId = (value instanceof Long) ? value : getId(value);
                    fieldNames.add(fieldName + ".id");
                    paramValues.add(refId);
                } else {
                    fieldNames.add(fieldName);
                    paramValues.add(value);
                }
            }

            if (!allResolved) continue;

            // Build JPQL: SELECT COUNT(e) FROM Entity e WHERE e.field1 = :p0 AND e.field2 = :p1 ...
            StringBuilder jpql = new StringBuilder("SELECT COUNT(e) FROM ")
                    .append(entityClass.getSimpleName()).append(" e WHERE ");
            for (int i = 0; i < fieldNames.size(); i++) {
                if (i > 0) jpql.append(" AND ");
                jpql.append("e.").append(fieldNames.get(i)).append(" = :p").append(i);
            }
            if (existingId != null) {
                jpql.append(" AND e.id <> :existingId");
            }

            var query = entityManager.createQuery(jpql.toString(), Long.class);
            for (int i = 0; i < paramValues.size(); i++) {
                query.setParameter("p" + i, paramValues.get(i));
            }
            if (existingId != null) {
                query.setParameter("existingId", existingId);
            }

            Long count = query.getSingleResult();
            if (count > 0) {
                // Build user-friendly field names (strip .id suffix)
                List<String> labels = fieldNames.stream()
                        .map(f -> f.endsWith(".id") ? f.substring(0, f.length() - 3) : f)
                        .toList();
                throw new IllegalArgumentException(
                        "Duplicate: an entry with the same " +
                        String.join(" and ", labels) + " already exists");
            }
        }
    }

    private String mapColumnToField(Class<?> entityClass, String columnName) {
        for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
            jakarta.persistence.JoinColumn jc = field.getAnnotation(jakarta.persistence.JoinColumn.class);
            if (jc != null && columnName.equals(jc.name())) return field.getName();
            jakarta.persistence.Column col = field.getAnnotation(jakarta.persistence.Column.class);
            if (col != null && columnName.equals(col.name())) return field.getName();
        }
        return null;
    }

    private String resolveBindingPath(DataForm form, String elementCode) {
        DataFormElement element = form.getElements().get(elementCode);
        if (element != null
                && element.getDataBinding() != null
                && !element.getDataBinding().isEmpty()) {
            return element.getDataBinding();
        }
        return elementCode;
    }

    private void setProperty(Object entity, String fieldName, Object value) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : entity.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object converted;
                    if (isJpaEntity(paramType)) {
                        // Relationship: value is the referenced entity's ID
                        converted = resolveRelatedEntity(paramType, value);
                    } else {
                        converted = convertValue(value, paramType);
                    }
                    method.invoke(entity, converted);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to set " + fieldName + " on " + entity.getClass().getSimpleName(), e);
                }
            }
        }
        throw new IllegalArgumentException("No setter found for field '" + fieldName
                + "' on " + entity.getClass().getSimpleName());
    }

    private Object getProperty(Object entity, String fieldName) {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : entity.getClass().getMethods()) {
            if (method.getName().equals(getterName) && method.getParameterCount() == 0) {
                try {
                    Object result = method.invoke(entity);
                    // If the result is a JPA entity (relationship), extract its ID
                    if (result != null && isJpaEntity(result.getClass())) {
                        return getId(result);
                    }
                    return result;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to get " + fieldName + " from " + entity.getClass().getSimpleName(), e);
                }
            }
        }
        throw new IllegalArgumentException("No getter found for field '" + fieldName
                + "' on " + entity.getClass().getSimpleName());
    }

    private boolean isJpaEntity(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private Object resolveRelatedEntity(Class<?> entityClass, Object idValue) {
        if (idValue == null) return null;
        Long id = Long.valueOf(idValue.toString());
        Object related = entityManager.find(entityClass, id);
        if (related == null) {
            throw new IllegalArgumentException("Related entity not found: "
                    + entityClass.getSimpleName() + " id=" + id);
        }
        return related;
    }

    private Long getId(Object entity) {
        try {
            Method getter = entity.getClass().getMethod("getId");
            return (Long) getter.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot read id from " + entity.getClass().getSimpleName(), e);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == Long.class || targetType == long.class) return Long.valueOf(value.toString());
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(value.toString());
        if (targetType == Double.class || targetType == double.class) return Double.valueOf(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(value.toString());
        if (targetType == YearMonth.class) {
            String s = value.toString();
            // Accept full date (yyyy-MM-dd) by truncating to yyyy-MM
            if (s.length() > 7 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                s = s.substring(0, 7);
            }
            return YearMonth.parse(s);
        }
        return value;
    }
}
