package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormData;

import java.lang.reflect.Method;
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

        applyValues(entity, data.getValues());
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

        for (String fieldCode : form.getElements().keySet()) {
            Object value = getProperty(entity, fieldCode);
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

    private void applyValues(Object entity, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            setProperty(entity, entry.getKey(), entry.getValue());
        }
    }

    private void setProperty(Object entity, String fieldName, Object value) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : entity.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    Object converted = convertValue(value, method.getParameterTypes()[0]);
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
                    return method.invoke(entity);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to get " + fieldName + " from " + entity.getClass().getSimpleName(), e);
                }
            }
        }
        throw new IllegalArgumentException("No getter found for field '" + fieldName
                + "' on " + entity.getClass().getSimpleName());
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
        return value;
    }
}
