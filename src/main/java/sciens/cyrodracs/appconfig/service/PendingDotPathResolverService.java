package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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
import sciens.cyrodracs.appconfig.ResolvedDotPathValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves dot-path column values for pending rows on embedded GRIDs per
 * gridElement.md G7.8 (Pending-Row Dot-Path Resolution).
 *
 * <p>Pending rows store only direct field IDs from the child editor (e.g.
 * {@code cameraLensMount = 1}). Dot-path columns like
 * {@code cameraLensMount.producer} (Inventor) need a resolved leaf id and
 * a rendered display string — neither of which the frontend can compute
 * without a backend round-trip. This service is that round-trip.</p>
 */
@Service
public class PendingDotPathResolverService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;

    public PendingDotPathResolverService(AppConfigStore appConfigStore,
                                         EntityManager entityManager,
                                         FilterExecutor filterExecutor) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
    }

    /**
     * Resolves the requested dot-path columns for each pending row.
     *
     * <p>Two overloads:</p>
     * <ul>
     *   <li>This one — caller supplies {@code rowEntityType} FQCN directly
     *       (used by tests and direct callers).</li>
     *   <li>{@link #resolve(String, String, List, List)} — caller supplies
     *       {@code (dataFormCode, elementCode)} for the GRID; service looks
     *       up the row entity type via the DataFormElement's
     *       {@code entityProviderRef}. Used by the GraphQL controller.</li>
     * </ul>
     *
     * @param rowEntityType FQCN of the GRID's row entity (e.g.
     *                      {@code sciens.cyrodracs.camera.CameraLensMount2CameraProducer})
     * @param directValues  one entry per (pendingRowIndex, fieldName) — the
     *                      direct field IDs the frontend captured at child-editor
     *                      save time
     * @param dotPathColumns the columns to resolve (columnKey may or may not
     *                       contain a dot; rendererRef is optional)
     * @return one entry per (pendingRowIndex, columnKey) tuple
     */
    @Transactional(readOnly = true)
    public List<ResolvedDotPathValue> resolve(String rowEntityType,
                                              List<DirectFieldValue> directValues,
                                              List<DotPathColumn> dotPathColumns) {
        if (rowEntityType == null || rowEntityType.isBlank()) {
            throw new IllegalArgumentException("rowEntityType is required");
        }
        if (directValues == null || dotPathColumns == null) {
            return List.of();
        }

        Class<?> sourceEntityClass = resolveClass(rowEntityType);
        Metamodel metamodel = entityManager.getMetamodel();
        AppConfig config = appConfigStore.getAppConfig();

        // Group directValues by pendingRowIndex → fieldName → id for O(1) lookup.
        Map<Integer, Map<String, Long>> rowDirect = new HashMap<>();
        for (DirectFieldValue dv : directValues) {
            rowDirect.computeIfAbsent(dv.pendingRowIndex(), k -> new HashMap<>())
                    .put(dv.fieldName(), dv.id());
        }

        List<ResolvedDotPathValue> out = new ArrayList<>();
        for (DotPathColumn col : dotPathColumns) {
            String columnKey = col.columnKey();
            int dotIndex = columnKey.indexOf('.');
            String firstSegment = dotIndex < 0 ? columnKey : columnKey.substring(0, dotIndex);
            String remainder = dotIndex < 0 ? "" : columnKey.substring(dotIndex + 1);

            Class<?> firstSegmentTargetType = resolveSingularTargetType(
                    sourceEntityClass, firstSegment, metamodel);

            Template template = resolveTemplate(config, col.rendererRef());

            for (Map.Entry<Integer, Map<String, Long>> rowEntry : rowDirect.entrySet()) {
                int pendingRowIndex = rowEntry.getKey();
                Long directId = rowEntry.getValue().get(firstSegment);

                if (directId == null || firstSegmentTargetType == null) {
                    out.add(new ResolvedDotPathValue(
                            pendingRowIndex, columnKey, null, null));
                    continue;
                }

                ResolvedLeaf leaf = resolveLeaf(firstSegmentTargetType,
                        directId, remainder);
                String display = (leaf.entity != null && template != null)
                        ? template.execute(leaf.entity)
                        : null;
                out.add(new ResolvedDotPathValue(
                        pendingRowIndex, columnKey, leaf.id, display));
            }
        }
        return out;
    }

    /**
     * Scope-based overload — resolves the GRID's row entity type from
     * the AppConfig DataFormElement's {@code entityProviderRef}, then
     * delegates to the FQCN-based overload.
     */
    @Transactional(readOnly = true)
    public List<ResolvedDotPathValue> resolve(String dataFormCode,
                                              String elementCode,
                                              List<DirectFieldValue> directValues,
                                              List<DotPathColumn> dotPathColumns) {
        if (dataFormCode == null || elementCode == null) {
            throw new IllegalArgumentException("dataFormCode and elementCode are required");
        }
        AppConfig config = appConfigStore.getAppConfig();
        DataForm form = config.getDataForms().get(dataFormCode);
        if (form == null) {
            throw new IllegalArgumentException("DataForm not found: " + dataFormCode);
        }
        DataFormElement element = form.getElements().get(elementCode);
        if (element == null || element.getType() != DataFormElementType.GRID) {
            throw new IllegalArgumentException("GRID element not found: " + elementCode);
        }
        EntityProvider provider = config.getEntityProviders()
                .get(element.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException(
                    "EntityProvider not found: " + element.getEntityProviderRef());
        }
        return resolve(provider.getEntityType().getFqcn(), directValues, dotPathColumns);
    }

    /** Holder for a leaf entity + its id resolved from a direct id + remainder. */
    private record ResolvedLeaf(Long id, Object entity) {}

    /**
     * For a direct id on the first-segment target type, walks the dot-path
     * remainder to the leaf entity, returning its id and the entity itself
     * (for Mustache rendering). Empty remainder = identity (returns the
     * first-segment entity directly).
     */
    @SuppressWarnings("unchecked")
    private ResolvedLeaf resolveLeaf(Class<?> firstSegmentTargetType,
                                     Long directId, String remainder) {
        if (remainder.isEmpty()) {
            // Direct column — load the first-segment entity by id; leafId == directId.
            Object entity = entityManager.find(firstSegmentTargetType, directId);
            return new ResolvedLeaf(entity == null ? null : directId, entity);
        }

        // Dot-path — build a Criteria query that selects the leaf entity and id
        // via the same walkPath machinery FilterExecutor uses for filter / sort.
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<?> root = query.from(firstSegmentTargetType);
        var leafPath = filterExecutor.walkPath(root, remainder);
        query.select((Expression<Object>) (Expression<?>) leafPath);
        query.where(cb.equal(root.get("id"), directId));

        try {
            Object leaf = entityManager.createQuery(query).getSingleResult();
            if (leaf == null) return new ResolvedLeaf(null, null);
            // Most leaves in our domain are entities; extract their id reflectively
            // via JPA metamodel.
            Long leafId = extractId(leaf);
            return new ResolvedLeaf(leafId, leaf);
        } catch (jakarta.persistence.NoResultException e) {
            return new ResolvedLeaf(null, null);
        }
    }

    /**
     * Resolves the target Java type of a direct singular association named
     * {@code segmentName} on {@code rootClass}. Returns null when the field
     * doesn't exist or isn't a singular association.
     */
    private Class<?> resolveSingularTargetType(Class<?> rootClass, String segmentName,
                                               Metamodel metamodel) {
        try {
            EntityType<?> entityType = metamodel.entity(rootClass);
            Attribute<?, ?> attr = entityType.getAttribute(segmentName);
            if (!(attr instanceof SingularAttribute<?, ?> singular)) return null;
            return singular.getJavaType();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Template resolveTemplate(AppConfig config, String rendererRef) {
        if (rendererRef == null || rendererRef.isBlank()) return null;
        EntityRenderer renderer = config.getEntityRenderers().get(rendererRef);
        if (renderer == null || renderer.getTemplate() == null) return null;
        return Mustache.compiler().compile(renderer.getTemplate());
    }

    private Long extractId(Object entity) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object value = idField.get(entity);
            if (value == null) return null;
            if (value instanceof Long l) return l;
            if (value instanceof Number n) return n.longValue();
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private Class<?> resolveClass(String fqcn) {
        try { return Class.forName(fqcn); }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }

    /** Input record — one per (pendingRowIndex, fieldName) tuple. */
    public record DirectFieldValue(int pendingRowIndex, String fieldName, Long id) {}

    /** Input record — one per dot-path column to resolve. */
    public record DotPathColumn(String columnKey, String rendererRef) {}
}
