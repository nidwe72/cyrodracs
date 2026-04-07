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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ViewDataService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;
    private final ColumnRenderer columnRenderer;

    public ViewDataService(AppConfigStore appConfigStore, EntityManager entityManager,
                           FilterExecutor filterExecutor, ColumnRenderer columnRenderer) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
        this.columnRenderer = columnRenderer;
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
            row.put("id", columnRenderer.getId(entity));
            for (TableColumn col : node.getTableColumns()) {
                row.put(col.getKey(), columnRenderer.resolveAndRender(
                        entity, col.getKey(), columnRenderers.get(col.getKey())));
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
        // Application-level cascade: delete all entities that reference this one via @ManyToOne
        deleteDependents(entityClass, entityId);
        entityManager.remove(entity);
    }

    /**
     * Finds and deletes all entities across all JPA entity types that have a @ManyToOne
     * reference to the given entity class and ID. This provides application-level cascade
     * delete for databases (like SQLite) that don't enforce FK constraints.
     */
    private void deleteDependents(Class<?> targetClass, Long targetId) {
        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            for (SingularAttribute<?, ?> attr : entityType.getSingularAttributes()) {
                if (attr.getJavaType().isAssignableFrom(targetClass)
                        || targetClass.isAssignableFrom(attr.getJavaType())) {
                    try {
                        String jpql = "DELETE FROM " + entityType.getName()
                                + " e WHERE e." + attr.getName() + ".id = :targetId";
                        entityManager.createQuery(jpql)
                                .setParameter("targetId", targetId)
                                .executeUpdate();
                    } catch (Exception ignored) {
                        // Attribute might not be a navigable relationship — skip
                    }
                }
            }
        }
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

    private Class<?> resolveClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }
}
