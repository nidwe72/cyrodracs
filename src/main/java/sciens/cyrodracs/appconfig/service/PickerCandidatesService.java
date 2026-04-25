package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.PickerCandidate;
import sciens.cyrodracs.appconfig.PickerCandidatesPagedResult;
import sciens.cyrodracs.appconfig.SortDirection;
import sciens.cyrodracs.appconfig.SortField;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves entity-ref column picker candidates: walks the table's metadata to
 * find the column's target entity type, projects the table's list filter onto
 * the picker entity (via {@link FilterProjector}), applies typeahead matching
 * across {@code EntityRenderer.searchFields}, orders by the renderer's
 * sortFields with an `id ASC` tiebreaker, and renders each candidate's label
 * server-side via the renderer's Mustache template.
 *
 * v1: skips {@code filterInjectableRef} (CF3.4.1 Janino paragraph).
 */
@Service
public class PickerCandidatesService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;

    public PickerCandidatesService(AppConfigStore appConfigStore,
                                   EntityManager entityManager,
                                   FilterExecutor filterExecutor) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
    }

    @Transactional(readOnly = true)
    public PickerCandidatesPagedResult getCandidates(String viewNodeCode,
                                                     String dataFormCode,
                                                     String elementCode,
                                                     String columnKey,
                                                     String term,
                                                     int page,
                                                     int pageSize) {
        if (columnKey == null || columnKey.isBlank()) {
            throw new IllegalArgumentException("columnKey is required");
        }

        boolean hasView = viewNodeCode != null && !viewNodeCode.isBlank();
        boolean hasGrid = (dataFormCode != null && !dataFormCode.isBlank())
                && (elementCode != null && !elementCode.isBlank());
        if (hasView == hasGrid) {
            throw new IllegalArgumentException(
                    "scope must specify either viewNodeCode or (dataFormCode + elementCode), not both");
        }

        AppConfig config = appConfigStore.getAppConfig();
        if (config == null) {
            throw new IllegalStateException("AppConfig not loaded");
        }

        Surface surface = hasView
                ? resolveFromViewNode(config, viewNodeCode)
                : resolveFromGrid(config, dataFormCode, elementCode);

        TableColumn column = surface.columns.stream()
                .filter(c -> columnKey.equals(c.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Column '" + columnKey + "' not found on the table"));

        // Resolve the target entity class via the JPA static metamodel.
        Class<?> sourceEntityClass = resolveClass(surface.provider.getEntityType().getFqcn());
        Metamodel metamodel = entityManager.getMetamodel();
        Class<?> targetEntityClass = resolveTargetEntity(sourceEntityClass, columnKey, metamodel);

        // Resolve the column's renderer (carries searchFields + sortFields + template).
        String rendererRef = column.getEntityRendererRef();
        EntityRenderer renderer = rendererRef == null
                ? null
                : config.getEntityRenderers().get(rendererRef);

        // Project the table's list filter onto the picker entity (skips injectable in v1).
        FilterNode projected = FilterProjector.project(surface.provider.getFilter(), columnKey);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // -- count query --
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> countRoot = countQuery.from(targetEntityClass);
        countQuery.select(cb.count(countRoot));
        Predicate countPred = combinePredicates(cb, countRoot, projected, renderer, term);
        if (countPred != null) countQuery.where(countPred);
        long totalCount = entityManager.createQuery(countQuery).getSingleResult();

        // -- data query --
        CriteriaQuery<Object> dataQuery = cb.createQuery(Object.class);
        Root<?> root = dataQuery.from(targetEntityClass);
        dataQuery.select(root);
        Predicate dataPred = combinePredicates(cb, root, projected, renderer, term);
        if (dataPred != null) dataQuery.where(dataPred);

        List<Order> orders = new ArrayList<>();
        if (renderer != null) {
            for (SortField sf : renderer.getSortFields()) {
                Path<?> path = filterExecutor.walkPath(root, sf.getField());
                orders.add(sf.getDirection() == SortDirection.DESC ? cb.desc(path) : cb.asc(path));
            }
        }
        orders.add(cb.asc(root.get("id")));
        dataQuery.orderBy(orders);

        TypedQuery<Object> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult(page * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<Object> rows = typedQuery.getResultList();

        // Render each candidate's label via the renderer's Mustache template.
        Template template = (renderer != null && renderer.getTemplate() != null)
                ? Mustache.compiler().compile(renderer.getTemplate())
                : null;

        List<PickerCandidate> items = new ArrayList<>(rows.size());
        for (Object entity : rows) {
            Long id = extractId(entity);
            String label = template != null ? template.execute(entity) : String.valueOf(id);
            items.add(new PickerCandidate(id, label));
        }
        return new PickerCandidatesPagedResult(items, totalCount, page, pageSize);
    }

    /**
     * Combines the projected list filter with a typeahead OR-predicate over
     * the renderer's searchFields. Either may be absent.
     */
    private Predicate combinePredicates(CriteriaBuilder cb, Root<?> root,
                                        FilterNode projected, EntityRenderer renderer,
                                        String term) {
        Predicate filterPred = projected == null
                ? null
                : filterExecutor.buildPredicate(projected, root, cb);

        Predicate typeaheadPred = buildTypeaheadPredicate(cb, root, renderer, term);

        if (filterPred != null && typeaheadPred != null) return cb.and(filterPred, typeaheadPred);
        if (filterPred != null) return filterPred;
        return typeaheadPred;
    }

    private Predicate buildTypeaheadPredicate(CriteriaBuilder cb, Root<?> root,
                                              EntityRenderer renderer, String term) {
        if (renderer == null || renderer.getSearchFields().isEmpty()) return null;
        if (term == null || term.isBlank()) return null;
        String pattern = "%" + term.toLowerCase() + "%";
        List<Predicate> ors = new ArrayList<>();
        for (String searchField : renderer.getSearchFields()) {
            Path<?> path = filterExecutor.walkPath(root, searchField);
            // For non-String fields, JPA's `path.as(String.class)` produces a
            // CAST-to-string at the query level (dialect-dependent on edge
            // types like Boolean — see CF3.5.1 cross-DB note).
            Expression<String> stringPath = cb.lower(path.as(String.class));
            ors.add(cb.like(stringPath, pattern));
        }
        return ors.size() == 1 ? ors.get(0) : cb.or(ors.toArray(new Predicate[0]));
    }

    /**
     * Walks dot-segments through the static metamodel, requiring the leaf
     * attribute to be a singular association (MANY_TO_ONE / ONE_TO_ONE) and
     * returning that association's target entity class.
     */
    private Class<?> resolveTargetEntity(Class<?> rootClass, String dotPath, Metamodel metamodel) {
        String[] segments = dotPath.split("\\.");
        EntityType<?> currentType = metamodel.entity(rootClass);
        Attribute<?, ?> attr = null;
        for (int i = 0; i < segments.length; i++) {
            attr = currentType.getAttribute(segments[i]);
            if (i < segments.length - 1) {
                if (!(attr instanceof SingularAttribute<?, ?> singular)) {
                    throw new IllegalArgumentException(
                            "Mid-path segment '" + segments[i] + "' is not singular");
                }
                currentType = metamodel.entity(singular.getJavaType());
            }
        }
        Attribute.PersistentAttributeType kind = attr.getPersistentAttributeType();
        if (kind != Attribute.PersistentAttributeType.MANY_TO_ONE
                && kind != Attribute.PersistentAttributeType.ONE_TO_ONE) {
            throw new IllegalArgumentException(
                    "Column '" + dotPath + "' is not an entity reference");
        }
        return attr.getJavaType();
    }

    private Long extractId(Object entity) {
        try {
            return (Long) entity.getClass().getMethod("getId").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Entity " + entity.getClass().getSimpleName() + " has no Long getId()", e);
        }
    }

    private record Surface(EntityProvider provider, List<TableColumn> columns) {}

    private Surface resolveFromViewNode(AppConfig config, String viewNodeCode) {
        ViewNode node = findViewNodeRecursive(config.getViewTree(), viewNodeCode);
        if (node == null) {
            throw new IllegalArgumentException("ViewNode not found: " + viewNodeCode);
        }
        if (node.getType() != ViewNodeType.ENTITY_LIST) {
            throw new IllegalArgumentException("ViewNode '" + viewNodeCode + "' is not ENTITY_LIST");
        }
        if (node.getEntityProviderRef() == null) {
            throw new IllegalStateException("ViewNode '" + viewNodeCode + "' has no entityProvider");
        }
        EntityProvider provider = config.getEntityProviders().get(node.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException(
                    "EntityProvider '" + node.getEntityProviderRef() + "' not found or has no entityType");
        }
        return new Surface(provider, node.getTableColumns());
    }

    private Surface resolveFromGrid(AppConfig config, String dataFormCode, String elementCode) {
        DataForm form = config.getDataForms().get(dataFormCode);
        if (form == null) {
            throw new IllegalArgumentException("DataForm not found: " + dataFormCode);
        }
        DataFormElement element = form.getElements().get(elementCode);
        if (element == null || element.getType() != DataFormElementType.GRID) {
            throw new IllegalArgumentException("GRID element not found: " + elementCode);
        }
        if (element.getEntityProviderRef() == null) {
            throw new IllegalStateException(
                    "GRID element '" + elementCode + "' has no entityProviderRef");
        }
        EntityProvider provider = config.getEntityProviders().get(element.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException(
                    "EntityProvider '" + element.getEntityProviderRef() + "' not found or has no entityType");
        }
        return new Surface(provider, element.getTableColumns());
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
