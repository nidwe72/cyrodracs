package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EnumValuesResult;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;
import sciens.cyrodracs.expression.ExpressionContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the option set for an ENUM column's filter dropdown per
 * columnFilters.md CF3.4.5 (restriction by visible rows) and CF3.4.6
 * (pending-row augmentation).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Resolve the column's TableColumn from AppConfig.</li>
 *   <li>Resolve the underlying enum class via the row entity's reflection
 *       chain (handles dot-path columns).</li>
 *   <li>Defensive: require {@code @Enumerated(EnumType.STRING)} on the
 *       leaf field. ORDINAL (or absence) throws loudly.</li>
 *   <li>If {@link TableColumn#isRestrictByVisibleRows()} is false:
 *       return all declared constants in declaration order. Done.</li>
 *   <li>Otherwise: materialise the row filter, AND-merge with stripped
 *       userFilter, run a {@code SELECT DISTINCT row.<columnKey>} query,
 *       union with pending values, intersect with declared constants
 *       (drops unknowns + nulls), sort by declaration order.</li>
 * </ol>
 *
 * <p>Frontend always calls this endpoint regardless of the flag —
 * backend handles both branches uniformly per the spec's
 * <em>Algorithm — backend handles the flag</em>.</p>
 */
@Service
public class EnumValuesService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;

    public EnumValuesService(AppConfigStore appConfigStore,
                             EntityManager entityManager,
                             FilterExecutor filterExecutor) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
    }

    @Transactional(readOnly = true)
    public EnumValuesResult getValues(String viewNodeCode,
                                      String dataFormCode,
                                      String elementCode,
                                      String columnKey,
                                      FilterNode userFilter,
                                      Long editorEntityId,
                                      Map<String, List<String>> pendingRowEnumValues) {
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
        if (config == null) throw new IllegalStateException("AppConfig not loaded");

        Surface surface = hasView
                ? resolveFromViewNode(config, viewNodeCode)
                : resolveFromGrid(config, dataFormCode, elementCode);

        TableColumn column = surface.columns.stream()
                .filter(c -> columnKey.equals(c.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Column '" + columnKey + "' not found on the table"));

        Class<?> rowEntityClass = resolveClass(surface.provider.getEntityType().getFqcn());

        // Resolve the leaf field via reflection — also lets us check the
        // @Enumerated annotation. JPA metamodel doesn't expose @Enumerated
        // mode, so reflection is the right tool here.
        Field leafField = walkToLeafField(rowEntityClass, columnKey);
        Class<?> enumClass = leafField.getType();
        if (!enumClass.isEnum()) {
            throw new IllegalArgumentException(
                    "Column '" + columnKey + "' is not an ENUM column");
        }

        // CF3.4.5 — defensive: only EnumType.STRING is supported.
        Enumerated enumeratedAnn = leafField.getAnnotation(Enumerated.class);
        if (enumeratedAnn == null || enumeratedAnn.value() == EnumType.ORDINAL) {
            throw new IllegalStateException(
                    "Column '" + columnKey + "' on " + rowEntityClass.getSimpleName()
                    + " uses EnumType.ORDINAL (or has no @Enumerated annotation) — "
                    + "restrictByVisibleRows requires @Enumerated(EnumType.STRING)");
        }

        // Declared constants in declaration order (used both as the
        // unrestricted return and as the upper-bound filter when restricted).
        List<String> declaredNames = new ArrayList<>();
        for (Object c : enumClass.getEnumConstants()) {
            declaredNames.add(((Enum<?>) c).name());
        }

        // Flag false → return all declared constants. Done.
        if (!column.isRestrictByVisibleRows()) {
            return new EnumValuesResult(declaredNames);
        }

        // Flag true → restricted DISTINCT + pending union + declared intersect.
        ExpressionContext expressionContext = buildExpressionContext(
                config, dataFormCode, editorEntityId);
        FilterNode materialisedBase = filterExecutor.materialiseFilter(
                surface.provider, expressionContext);
        FilterNode otherUserFilters = PickerCandidatesService.stripPickerOwnFilter(
                userFilter, columnKey);
        FilterNode totalRowFilter = FilterExecutor.mergeFilters(
                materialisedBase, otherUserFilters);

        // SELECT DISTINCT row.<columnKey> FROM <rowEntityClass> [WHERE …]
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<?> root = query.from(rowEntityClass);
        @SuppressWarnings("unchecked")
        Path<Object> columnPath = (Path<Object>) (Path<?>) filterExecutor.walkPath(root, columnKey);
        query.select(columnPath).distinct(true);
        if (totalRowFilter != null) {
            Predicate where = filterExecutor.buildPredicate(totalRowFilter, root, cb);
            if (where != null) query.where(where);
        }
        List<?> rows = entityManager.createQuery(query).getResultList();

        // Collect present enum names (skip nulls; @Enumerated(EnumType.STRING)
        // returns enum instances, but defensively coerce to string via .name()
        // when possible).
        Set<String> present = new LinkedHashSet<>();
        for (Object r : rows) {
            if (r == null) continue;
            if (r instanceof Enum<?> e) {
                present.add(e.name());
            } else {
                present.add(r.toString());
            }
        }

        // CF3.4.6 — union with pending row enum values for this column.
        if (pendingRowEnumValues != null) {
            List<String> pendingForColumn = pendingRowEnumValues.get(columnKey);
            if (pendingForColumn != null) {
                present.addAll(pendingForColumn);
            }
        }

        // Intersect with declared constants (defensive — drops unknowns
        // from stale frontend / pending values from a different version of
        // the enum) AND yield in declaration order.
        List<String> result = new ArrayList<>();
        for (String declared : declaredNames) {
            if (present.contains(declared)) result.add(declared);
        }
        return new EnumValuesResult(result);
    }

    /** Walks dot-segments via reflection on declared fields. */
    private Field walkToLeafField(Class<?> rootClass, String dotPath) {
        String[] segments = dotPath.split("\\.");
        Class<?> current = rootClass;
        Field field = null;
        for (String segment : segments) {
            field = findFieldOnClass(current, segment);
            current = field.getType();
        }
        return field;
    }

    private Field findFieldOnClass(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new IllegalArgumentException(
                "Field '" + name + "' not found on " + clazz.getName());
    }

    // ── Surface resolution (mirror of PickerCandidatesService) ─────────────

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

    private Class<?> resolveClass(String fqcn) {
        try { return Class.forName(fqcn); }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }
}
