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
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ViewDataService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;

    public ViewDataService(AppConfigStore appConfigStore, EntityManager entityManager,
                           FilterExecutor filterExecutor) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
    }

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getData(String viewNodeCode) {
        return getDataPaged(viewNodeCode, 0, DEFAULT_PAGE_SIZE).items();
    }

    @Transactional(readOnly = true)
    public PagedResult getDataPaged(String viewNodeCode, int page, int pageSize) {
        ViewNode node = resolveViewNode(viewNodeCode);
        if (node.getType() != ViewNodeType.ENTITY_LIST) {
            throw new IllegalArgumentException("ViewNode '" + viewNodeCode + "' is not ENTITY_LIST");
        }
        if (node.getEntityProviderRef() == null) {
            throw new IllegalStateException("ViewNode '" + viewNodeCode + "' has no entityProvider");
        }

        AppConfig config = appConfigStore.getAppConfig();
        EntityProvider provider = config.getEntityProviders().get(node.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException("EntityProvider '" + node.getEntityProviderRef() + "' not found or has no entityType");
        }

        Class<?> entityClass = resolveClass(provider.getEntityType().getFqcn());
        int offset = page * pageSize;
        FilterExecutor.PagedResult paged = filterExecutor.executePagedQuery(provider, entityClass, offset, pageSize);

        // Pre-compile renderers for columns that have them
        Map<String, Template> columnRenderers = new LinkedHashMap<>();
        for (TableColumn col : node.getTableColumns()) {
            if (col.getEntityRendererRef() != null) {
                EntityRenderer renderer = config.getEntityRenderers().get(col.getEntityRendererRef());
                if (renderer != null && renderer.getTemplate() != null) {
                    columnRenderers.put(col.getKey(), Mustache.compiler().compile(renderer.getTemplate()));
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entity : paged.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            // Always include id for edit/delete actions
            row.put("id", getId(entity));
            for (TableColumn col : node.getTableColumns()) {
                String key = col.getKey();
                Object value = getProperty(entity, key);

                if (value != null && isJpaEntity(value.getClass())) {
                    // Relationship column
                    Template template = columnRenderers.get(key);
                    if (template != null) {
                        // Render via Mustache template
                        Map<String, Object> context = buildEntityContext(value);
                        row.put(key, template.execute(context));
                    } else {
                        // Fallback: extract ID
                        row.put(key, getId(value));
                    }
                } else {
                    row.put(key, value);
                }
            }
            rows.add(row);
        }
        return new PagedResult(rows, paged.totalCount(), page, pageSize);
    }

    /** Holds a page of mapped rows together with pagination metadata. */
    public record PagedResult(
            List<Map<String, Object>> items,
            long totalCount,
            int page,
            int pageSize
    ) {
        public int totalPages() {
            return (int) Math.ceil((double) totalCount / pageSize);
        }
    }

    @Transactional
    public void delete(String viewNodeCode, Long entityId) {
        ViewNode node = resolveViewNode(viewNodeCode);
        if (node.getType() != ViewNodeType.ENTITY_LIST) {
            throw new IllegalArgumentException("ViewNode '" + viewNodeCode + "' is not ENTITY_LIST");
        }

        AppConfig config = appConfigStore.getAppConfig();
        EntityProvider provider = config.getEntityProviders().get(node.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException("EntityProvider not found for ViewNode '" + viewNodeCode + "'");
        }

        Class<?> entityClass = resolveClass(provider.getEntityType().getFqcn());
        Object entity = entityManager.find(entityClass, entityId);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found: " + entityClass.getSimpleName() + " id=" + entityId);
        }
        entityManager.remove(entity);
    }

    private ViewNode resolveViewNode(String code) {
        AppConfig config = appConfigStore.getAppConfig();
        if (config == null) {
            throw new IllegalStateException("AppConfig not loaded");
        }
        ViewNode node = findViewNodeRecursive(config.getViewTree(), code);
        if (node == null) {
            throw new IllegalArgumentException("ViewNode not found: " + code);
        }
        return node;
    }

    private ViewNode findViewNodeRecursive(Map<String, ViewNode> nodes, String code) {
        ViewNode direct = nodes.get(code);
        if (direct != null) return direct;
        for (ViewNode node : nodes.values()) {
            if (!node.getChildren().isEmpty()) {
                Map<String, ViewNode> childMap = new LinkedHashMap<>();
                for (ViewNode child : node.getChildren()) {
                    childMap.put(child.getCode(), child);
                }
                ViewNode found = findViewNodeRecursive(childMap, code);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Map<String, Object> buildEntityContext(Object entity) {
        Map<String, Object> context = new LinkedHashMap<>();
        EntityType<?> metamodel = entityManager.getMetamodel().entity(resolveEntityClass(entity));
        for (SingularAttribute<?, ?> attr : metamodel.getSingularAttributes()) {
            if ("id".equals(attr.getName())) continue;
            if (attr.getPersistentAttributeType() != jakarta.persistence.metamodel.Attribute.PersistentAttributeType.BASIC) continue;
            Object value = getProperty(entity, attr.getName());
            if (value != null) {
                context.put(attr.getName(), value.toString());
            }
        }
        return context;
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
            return null;
        }
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

    /**
     * Resolves the actual @Entity-annotated class from a potentially proxied instance.
     */
    private Class<?> resolveEntityClass(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                return current;
            }
            current = current.getSuperclass();
        }
        return entity.getClass();
    }

    private Class<?> resolveClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }
}
