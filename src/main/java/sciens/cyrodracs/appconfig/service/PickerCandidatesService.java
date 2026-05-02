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
import jakarta.persistence.criteria.Subquery;
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
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.PickerCandidate;
import sciens.cyrodracs.appconfig.PickerCandidatesPagedResult;
import sciens.cyrodracs.appconfig.SortDirection;
import sciens.cyrodracs.appconfig.SortField;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;
import sciens.cyrodracs.expression.ExpressionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves entity-ref column picker candidates per CF3.4 (CF3.4.1 projection
 * and CF3.4.3 distinct-row restriction). The runtime picks per picker-open:
 *
 * <ol>
 *   <li>Materialise the surface's row predicate via
 *       {@link FilterExecutor#materialiseFilter} — runs Janino once and
 *       AND-merges with the static filter.</li>
 *   <li>AND-combine the materialised filter with {@code otherUserFilters}
 *       (the picker's-own-column-stripped user filter — see
 *       {@link #stripPickerOwnFilter}).</li>
 *   <li>Try CF3.4.1's projection on the combined tree. If non-null, use it as
 *       the candidate-side WHERE — cheap, no DB subquery.</li>
 *   <li>Otherwise (cross-entity / Janino-only / mixed groups that drop most
 *       clauses, or no row predicate at all), fall back to CF3.4.3: build an
 *       inner DISTINCT subquery on the surface's row entity and IN-restrict
 *       the candidate query. Runs unconditionally when CF3.4.1 doesn't
 *       apply — a junction-table GRID with no row predicate still constrains
 *       candidates to values that actually appear in some row (Excel-autofilter
 *       convention).</li>
 * </ol>
 *
 * <p>Typeahead matching, ordering, and Mustache rendering are unchanged from
 * earlier phases (CF3.4.2 / CF3.5.1).
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

    /**
     * @deprecated CF3.4.3 protocol carries userFilter + editorEntityId; prefer the
     * 9-arg overload. Kept for tests/internal callers that don't need them.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public PickerCandidatesPagedResult getCandidates(String viewNodeCode,
                                                     String dataFormCode,
                                                     String elementCode,
                                                     String columnKey,
                                                     String term,
                                                     int page,
                                                     int pageSize) {
        return getCandidates(viewNodeCode, dataFormCode, elementCode,
                columnKey, term, page, pageSize, null, null);
    }

    @Transactional(readOnly = true)
    public PickerCandidatesPagedResult getCandidates(String viewNodeCode,
                                                     String dataFormCode,
                                                     String elementCode,
                                                     String columnKey,
                                                     String term,
                                                     int page,
                                                     int pageSize,
                                                     FilterNode userFilter,
                                                     Long editorEntityId) {
        return getCandidates(viewNodeCode, dataFormCode, elementCode,
                columnKey, term, page, pageSize, userFilter, editorEntityId, null);
    }

    /**
     * CF3.4.4 entry point — same as the 9-arg overload, plus an optional
     * {@code pendingRowDirectValues} map keyed by direct field name on the
     * row entity, holding the referenced committed-entity ids from each
     * pending row's value at that field. Entries unrelated to the picker's
     * column are tolerated and ignored. {@code null} or empty map means
     * "no pending augmentation" — picker reduces to CF3.4.3's behaviour.
     */
    @Transactional(readOnly = true)
    public PickerCandidatesPagedResult getCandidates(String viewNodeCode,
                                                     String dataFormCode,
                                                     String elementCode,
                                                     String columnKey,
                                                     String term,
                                                     int page,
                                                     int pageSize,
                                                     FilterNode userFilter,
                                                     Long editorEntityId,
                                                     Map<String, List<Long>> pendingRowDirectValues) {
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

        // Resolve the target entity class via the JPA static metamodel. Per CF3.4.3
        // *Edge cases* — a non-ENTITY_REF columnKey is rejected loudly: pickers exist
        // only for ENTITY_REF columns (CF1.2). The frontend reads the column's
        // filterType from columnFilterMetadata and MUST NOT issue a picker request
        // for any other type. A failure here signals a client-side defect.
        Class<?> sourceEntityClass = resolveClass(surface.provider.getEntityType().getFqcn());
        Metamodel metamodel = entityManager.getMetamodel();
        Class<?> targetEntityClass = resolveTargetEntity(sourceEntityClass, columnKey, metamodel);

        // Strip the picker's own column from userFilter → otherUserFilters.
        FilterNode otherUserFilters = stripPickerOwnFilter(userFilter, columnKey);

        // Resolve the column's renderer (carries searchFields + sortFields + template).
        String rendererRef = column.getEntityRendererRef();
        EntityRenderer renderer = rendererRef == null
                ? null
                : config.getEntityRenderers().get(rendererRef);

        // ── CF3.4 algorithm — Phase 3 ──────────────────────────────────
        // (1) Materialise the surface's row predicate (runs Janino once).
        // (2) AND-combine with otherUserFilters → totalRowFilter (the
        //     effective row predicate for both projection and inner DISTINCT).
        // (3) Try CF3.4.1 projection on totalRowFilter; if non-null, use it
        //     as the candidate-side WHERE (no DB subquery — cheap path).
        // (4) Else, fall back to CF3.4.3: inner DISTINCT subquery on the
        //     row entity, IN-restricting the candidate query.
        // (5) If totalRowFilter is null → short-circuit to unrestricted.
        ExpressionContext expressionContext = buildExpressionContext(
                config, dataFormCode, editorEntityId);
        FilterNode materialisedBase = filterExecutor.materialiseFilter(
                surface.provider, expressionContext);
        FilterNode totalRowFilter = FilterExecutor.mergeFilters(
                materialisedBase, otherUserFilters);
        FilterNode projected = totalRowFilter == null
                ? null
                : FilterProjector.project(totalRowFilter, columnKey);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // CF3.4.4 — resolve pending direct IDs for the column's first segment.
        // Empty list (or no entry) means no pending augmentation.
        List<Long> pendingDirectIds = resolvePendingDirectIds(
                columnKey, pendingRowDirectValues);

        // -- count query --
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> countRoot = countQuery.from(targetEntityClass);
        countQuery.select(cb.count(countRoot));
        Predicate countPred = buildPickerWhere(cb, countQuery, countRoot,
                projected, totalRowFilter, sourceEntityClass, columnKey, renderer, term,
                pendingDirectIds);
        if (countPred != null) countQuery.where(countPred);
        long totalCount = entityManager.createQuery(countQuery).getSingleResult();

        // -- data query --
        CriteriaQuery<Object> dataQuery = cb.createQuery(Object.class);
        Root<?> root = dataQuery.from(targetEntityClass);
        dataQuery.select(root);
        Predicate dataPred = buildPickerWhere(cb, dataQuery, root,
                projected, totalRowFilter, sourceEntityClass, columnKey, renderer, term,
                pendingDirectIds);
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
     * Builds the picker query's effective WHERE — the AND-composition of:
     * <ul>
     *   <li>The CF3.4.1 projection (when non-null) OR a CF3.4.3 inner-DISTINCT
     *       subquery's IN-clause OR nothing (short-circuit when no row
     *       predicate exists).</li>
     *   <li>The typeahead OR-predicate over {@code EntityRenderer.searchFields}.</li>
     * </ul>
     * Returns {@code null} when neither a restriction nor a typeahead applies.
     */
    @SuppressWarnings("unchecked")
    private Predicate buildPickerWhere(CriteriaBuilder cb,
                                       jakarta.persistence.criteria.AbstractQuery<?> parentQuery,
                                       Root<?> candidate,
                                       FilterNode projected,
                                       FilterNode totalRowFilter,
                                       Class<?> sourceEntityClass,
                                       String columnKey,
                                       EntityRenderer renderer,
                                       String term,
                                       List<Long> pendingDirectIds) {
        Predicate restrictionPred;
        if (projected != null) {
            // CF3.4.1 — projection clean; build candidate-side predicate from it.
            restrictionPred = filterExecutor.buildPredicate(projected, candidate, cb);
        } else {
            // CF3.4.3 — inner DISTINCT subquery on the row entity. Runs even when
            // totalRowFilter is null: a junction-table GRID (e.g. lensMountMappings)
            // still constrains candidates to the distinct values that actually appear
            // in any row, even with no row predicate. Excel-autofilter convention —
            // only offer values the user could ever encounter on this surface.
            //   SELECT DISTINCT row.<columnKey>.id FROM <sourceEntityClass> row [WHERE …]
            Subquery<Long> sub = parentQuery.subquery(Long.class);
            Root<?> rowRoot = sub.from(sourceEntityClass);
            Expression<Long> innerSelect = (Expression<Long>) (Expression<?>)
                    filterExecutor.walkPath(rowRoot, columnKey + ".id");
            sub.select(innerSelect).distinct(true);
            if (totalRowFilter != null) {
                Predicate innerWhere = filterExecutor.buildPredicate(totalRowFilter, rowRoot, cb);
                if (innerWhere != null) sub.where(innerWhere);
            }

            Path<?> candidateId = candidate.get("id");
            restrictionPred = candidateId.in(sub);
        }

        // CF3.4.4 — OR-augment the candidate set with pending-row-derived IDs.
        // For a direct column key (no dot), pendingDirectIds are themselves the
        // augmentation. For a dot-path key, walk the remainder over the row entity's
        // first-segment target type to produce the actual candidate IDs via subquery.
        Predicate pendingPred = buildPendingAugmentation(cb, parentQuery, candidate,
                sourceEntityClass, columnKey, pendingDirectIds);
        if (pendingPred != null) {
            restrictionPred = restrictionPred == null
                    ? pendingPred
                    : cb.or(restrictionPred, pendingPred);
        }

        Predicate typeaheadPred = buildTypeaheadPredicate(cb, candidate, renderer, term);

        if (restrictionPred != null && typeaheadPred != null) {
            return cb.and(restrictionPred, typeaheadPred);
        }
        if (restrictionPred != null) return restrictionPred;
        return typeaheadPred;
    }

    /**
     * CF3.4.4 — resolves the pending IDs for the picker's column key into the
     * candidate-side augmentation predicate.
     *
     * <p>For a direct column key (`firstSegment` only, no remainder): the
     * pending IDs are themselves the candidate-side IDs.</p>
     *
     * <p>For a dot-path key (`firstSegment.remainder`): walks the remainder
     * over the row entity's first-segment target type via a subquery,
     * producing the candidate-side IDs:
     * <pre>
     *   SELECT firstSegmentEntity.&lt;remainder&gt;.id
     *   FROM   &lt;firstSegmentTargetType&gt; firstSegmentEntity
     *   WHERE  firstSegmentEntity.id IN (:pendingDirectIds)
     * </pre>
     * </p>
     *
     * <p>Returns {@code null} when {@code pendingDirectIds} is null/empty.</p>
     */
    @SuppressWarnings("unchecked")
    private Predicate buildPendingAugmentation(CriteriaBuilder cb,
                                               jakarta.persistence.criteria.AbstractQuery<?> parentQuery,
                                               Root<?> candidate,
                                               Class<?> sourceEntityClass,
                                               String columnKey,
                                               List<Long> pendingDirectIds) {
        if (pendingDirectIds == null || pendingDirectIds.isEmpty()) return null;

        int dotIndex = columnKey.indexOf('.');
        Path<?> candidateId = candidate.get("id");

        if (dotIndex < 0) {
            // Direct column — pendingDirectIds ARE the candidate IDs.
            return candidateId.in(pendingDirectIds);
        }

        // Dot-path column — walk the remainder over the first-segment target type.
        String firstSegment = columnKey.substring(0, dotIndex);
        String remainder = columnKey.substring(dotIndex + 1);
        Class<?> firstSegmentTargetType = resolveFirstSegmentTargetType(
                sourceEntityClass, firstSegment);
        if (firstSegmentTargetType == null) return null;

        Subquery<Long> sub = parentQuery.subquery(Long.class);
        Root<?> firstSegRoot = sub.from(firstSegmentTargetType);
        Expression<Long> remainderId = (Expression<Long>) (Expression<?>)
                filterExecutor.walkPath(firstSegRoot, remainder + ".id");
        sub.select(remainderId);
        sub.where(firstSegRoot.get("id").in(pendingDirectIds));
        return candidateId.in(sub);
    }

    /**
     * Resolves the picker's pending IDs from the request's
     * {@code pendingRowDirectValues} payload by looking up the entry for the
     * column key's first segment. Returns null when no entry matches.
     */
    private List<Long> resolvePendingDirectIds(String columnKey,
                                               Map<String, List<Long>> pendingRowDirectValues) {
        if (pendingRowDirectValues == null || pendingRowDirectValues.isEmpty()) return null;
        int dotIndex = columnKey.indexOf('.');
        String firstSegment = dotIndex < 0 ? columnKey : columnKey.substring(0, dotIndex);
        return pendingRowDirectValues.get(firstSegment);
    }

    /**
     * Resolves the Java target type of a direct singular association named
     * {@code firstSegment} on {@code sourceEntityClass}. Returns null if the
     * field doesn't exist or isn't a singular association — caller treats
     * those as "no augmentation" (admin config error surfaces elsewhere).
     */
    private Class<?> resolveFirstSegmentTargetType(Class<?> sourceEntityClass,
                                                   String firstSegment) {
        try {
            EntityType<?> entityType = entityManager.getMetamodel().entity(sourceEntityClass);
            Attribute<?, ?> attr = entityType.getAttribute(firstSegment);
            if (!(attr instanceof SingularAttribute<?, ?> singular)) return null;
            return singular.getJavaType();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Builds the {@link ExpressionContext} the surface's
     * {@code filterInjectableRef} sees at picker-query time. For GRID surfaces
     * we load the editor entity from the parent DataForm; ENTITY_LIST surfaces
     * have no parent editor and a null context produces a null Janino result
     * (the standard "no editor entity available" pattern, see
     * {@code producerMountFilter}).
     */
    private ExpressionContext buildExpressionContext(AppConfig config,
                                                     String dataFormCode,
                                                     Long editorEntityId) {
        ExpressionContext context = new ExpressionContext();
        if (editorEntityId == null || dataFormCode == null) return context;

        DataForm form = config.getDataForms().get(dataFormCode);
        if (form == null || form.getEntity() == null) return context;
        Class<?> editorEntityClass = resolveClass(form.getEntity().getFqcn());
        Object editorEntity = entityManager.find(editorEntityClass, editorEntityId);
        context.put("editor", editorEntity);
        return context;
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

    /**
     * Removes nodes whose {@code field == pickerColumnKey} from the user-filter
     * tree, producing the "other user filters" set for CF3.4.3's inner DISTINCT.
     * v1 frontend sends a single-level AND_GROUP per CF4.3, but the helper is
     * shape-agnostic and forward-compatible with v2's nested groups.
     *
     * <p>Excel-autofilter convention: when the user has already selected a
     * value in column K and reopens K's picker, K's own filter MUST NOT
     * collapse the candidate set to that value — otherwise the user can never
     * switch to a different value.
     */
    static FilterNode stripPickerOwnFilter(FilterNode userFilter, String pickerColumnKey) {
        if (userFilter == null || pickerColumnKey == null) return userFilter;
        FilterNodeType type = userFilter.getType();
        if (type == FilterNodeType.COMPARISON) {
            String field = userFilter.getField();
            if (field == null) return userFilter;
            // Match exact column key (e.g. STRING column "name") OR any sub-path of it
            // (e.g. ENTITY_REF column "producer" filtered as "producer.id"). Both forms
            // belong to the picker's own column.
            String prefix = pickerColumnKey + ".";
            if (field.equals(pickerColumnKey) || field.startsWith(prefix)) return null;
            return userFilter;
        }
        if (type == FilterNodeType.AND_GROUP || type == FilterNodeType.OR_GROUP) {
            List<FilterNode> kept = new ArrayList<>();
            List<FilterNode> children = userFilter.getChildren();
            if (children != null) {
                for (FilterNode child : children) {
                    FilterNode stripped = stripPickerOwnFilter(child, pickerColumnKey);
                    if (stripped != null) kept.add(stripped);
                }
            }
            if (kept.isEmpty()) return null;
            if (kept.size() == 1 && type == FilterNodeType.AND_GROUP) return kept.get(0);
            FilterNode result = new FilterNode();
            result.setType(type);
            result.setChildren(kept);
            return result;
        }
        return userFilter;
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
