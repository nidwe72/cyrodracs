package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.EntityOption;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntitySelectService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;

    public EntitySelectService(AppConfigStore appConfigStore, EntityManager entityManager,
                               FilterExecutor filterExecutor) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
    }

    @Transactional(readOnly = true)
    public List<EntityOption> getOptions(String providerCode, String rendererCode) {
        AppConfig config = appConfigStore.getAppConfig();
        if (config == null) {
            throw new IllegalStateException("AppConfig not loaded");
        }

        EntityProvider provider = config.getEntityProviders().get(providerCode);
        if (provider == null) {
            throw new IllegalArgumentException("EntityProvider not found: " + providerCode);
        }
        if (provider.getEntityType() == null) {
            throw new IllegalStateException("EntityProvider '" + providerCode + "' has no entityType");
        }

        EntityRenderer renderer = config.getEntityRenderers().get(rendererCode);
        if (renderer == null) {
            throw new IllegalArgumentException("EntityRenderer not found: " + rendererCode);
        }
        if (renderer.getTemplate() == null || renderer.getTemplate().isEmpty()) {
            throw new IllegalStateException("EntityRenderer '" + rendererCode + "' has no template");
        }

        Class<?> entityClass = resolveClass(provider.getEntityType().getFqcn());
        List<?> entities = filterExecutor.executeQuery(provider, entityClass);

        Template template = Mustache.compiler().compile(renderer.getTemplate());

        List<EntityOption> options = new ArrayList<>();
        for (Object entity : entities) {
            Long id = getId(entity);
            Map<String, Object> context = buildContext(entity, entityClass);
            String label = template.execute(context);
            options.add(new EntityOption(id, label));
        }
        return options;
    }

    private Map<String, Object> buildContext(Object entity, Class<?> entityClass) {
        Map<String, Object> context = new LinkedHashMap<>();
        EntityType<?> metamodel = entityManager.getMetamodel().entity(entityClass);
        for (SingularAttribute<?, ?> attr : metamodel.getSingularAttributes()) {
            if ("id".equals(attr.getName())) continue;
            Object value = getProperty(entity, attr.getName());
            if (value != null) {
                if (isJpaEntity(value.getClass())) {
                    // Nested relationship: build a sub-context for Mustache {{relation.field}}
                    context.put(attr.getName(), buildContext(value, value.getClass()));
                } else {
                    context.put(attr.getName(), value.toString());
                }
            }
        }
        return context;
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

    private Long getId(Object entity) {
        try {
            Method getter = entity.getClass().getMethod("getId");
            return (Long) getter.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot read id from " + entity.getClass().getSimpleName(), e);
        }
    }

    private Class<?> resolveClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }
}
