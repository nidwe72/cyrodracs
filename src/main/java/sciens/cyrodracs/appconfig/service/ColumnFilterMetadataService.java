package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.ColumnFilterMeta;
import sciens.cyrodracs.appconfig.ColumnFilterType;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes per-column filter metadata for a table surface (ENTITY_LIST ViewNode
 * or GRID DataFormElement). Walks the JPA static metamodel from the table's
 * entity class through each column's dot-path and maps the leaf attribute to
 * a ColumnFilterType, so the frontend can render the correct inline input.
 */
@Service
public class ColumnFilterMetadataService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;

    public ColumnFilterMetadataService(AppConfigStore appConfigStore, EntityManager entityManager) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ColumnFilterMeta> getMetadata(String viewNodeCode,
                                              String dataFormCode,
                                              String elementCode) {
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

        EntityProviderAndColumns resolved = hasView
                ? resolveFromViewNode(config, viewNodeCode)
                : resolveFromGrid(config, dataFormCode, elementCode);

        Class<?> entityClass = resolveClass(resolved.provider.getEntityType().getFqcn());
        Metamodel metamodel = entityManager.getMetamodel();

        List<ColumnFilterMeta> metas = new ArrayList<>();
        for (TableColumn col : resolved.columns) {
            metas.add(buildMeta(col, entityClass, metamodel, config));
        }
        return metas;
    }

    private ColumnFilterMeta buildMeta(TableColumn col, Class<?> rootClass,
                                       Metamodel metamodel, AppConfig config) {
        ColumnFilterMeta meta = new ColumnFilterMeta();
        meta.setColumnKey(col.getKey());
        meta.setFilterType(ColumnFilterType.UNSUPPORTED);

        Attribute<?, ?> leaf;
        try {
            leaf = walkToLeaf(rootClass, col.getKey(), metamodel);
        } catch (RuntimeException e) {
            // Path not resolvable through metamodel — leave as UNSUPPORTED
            return meta;
        }

        Attribute.PersistentAttributeType kind = leaf.getPersistentAttributeType();
        Class<?> javaType = leaf.getJavaType();

        if (kind == Attribute.PersistentAttributeType.MANY_TO_ONE
                || kind == Attribute.PersistentAttributeType.ONE_TO_ONE) {
            // ENTITY_REF requires a renderer with non-empty searchFields,
            // otherwise the picker has nothing to typeahead-match on (CF3.4).
            String rendererRef = col.getEntityRendererRef();
            EntityRenderer renderer = rendererRef == null
                    ? null
                    : config.getEntityRenderers().get(rendererRef);
            boolean searchable = renderer != null
                    && renderer.getSearchFields() != null
                    && !renderer.getSearchFields().isEmpty();
            if (!searchable) {
                return meta; // UNSUPPORTED
            }
            meta.setFilterType(ColumnFilterType.ENTITY_REF);
            meta.setEntityRendererRef(rendererRef);
            return meta;
        }

        if (kind != Attribute.PersistentAttributeType.BASIC) {
            return meta; // collections / embedded — UNSUPPORTED in v1
        }

        if (javaType.isEnum()) {
            meta.setFilterType(ColumnFilterType.ENUM);
            List<String> values = new ArrayList<>();
            for (Object c : javaType.getEnumConstants()) {
                values.add(((Enum<?>) c).name());
            }
            meta.setEnumValues(values);
            return meta;
        }

        ColumnFilterType mapped = mapBasicType(javaType);
        meta.setFilterType(mapped);
        return meta;
    }

    /**
     * Walks a dot-separated path through the JPA static metamodel (no Root /
     * no in-flight query). For mid-path segments the attribute must be
     * SingularAttribute pointing at another entity; otherwise the path is not
     * navigable and the caller treats it as UNSUPPORTED.
     */
    private Attribute<?, ?> walkToLeaf(Class<?> rootClass, String dotPath, Metamodel metamodel) {
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
        return attr;
    }

    private ColumnFilterType mapBasicType(Class<?> t) {
        if (t == String.class) return ColumnFilterType.STRING;
        if (t == Integer.class || t == int.class
                || t == Long.class || t == long.class
                || t == Short.class || t == short.class
                || t == BigInteger.class
                || t == Double.class || t == double.class
                || t == Float.class || t == float.class
                || t == BigDecimal.class) return ColumnFilterType.NUMBER;
        if (t == LocalDate.class || t == java.sql.Date.class) return ColumnFilterType.DATE;
        if (t == YearMonth.class) return ColumnFilterType.YEAR_MONTH;
        if (t == LocalDateTime.class || t == Instant.class || t == OffsetDateTime.class)
            return ColumnFilterType.DATETIME;
        if (t == Boolean.class || t == boolean.class) return ColumnFilterType.BOOLEAN;
        return ColumnFilterType.UNSUPPORTED;
    }

    private record EntityProviderAndColumns(EntityProvider provider, List<TableColumn> columns) {}

    private EntityProviderAndColumns resolveFromViewNode(AppConfig config, String viewNodeCode) {
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
        return new EntityProviderAndColumns(provider, node.getTableColumns());
    }

    private EntityProviderAndColumns resolveFromGrid(AppConfig config,
                                                     String dataFormCode, String elementCode) {
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
        return new EntityProviderAndColumns(provider, element.getTableColumns());
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
