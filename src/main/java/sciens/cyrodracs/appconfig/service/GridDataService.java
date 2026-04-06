package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.expression.EditorEntityBuilder;
import sciens.cyrodracs.expression.ExpressionContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GridDataService {

    private final AppConfigStore appConfigStore;
    private final EntityManager entityManager;
    private final FilterExecutor filterExecutor;
    private final EditorEntityBuilder editorEntityBuilder;

    public GridDataService(AppConfigStore appConfigStore, EntityManager entityManager,
                           FilterExecutor filterExecutor, EditorEntityBuilder editorEntityBuilder) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
        this.editorEntityBuilder = editorEntityBuilder;
    }

    public record GridPagedResult(
            List<Map<String, Object>> items,
            long totalCount,
            int page,
            int pageSize
    ) {
        public int totalPages() {
            return pageSize == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        }
    }

    @Transactional(readOnly = true)
    public GridPagedResult getGridData(String dataFormCode, String elementCode,
                                       Long entityId, Map<String, String> formState,
                                       int page, int pageSize) {
        AppConfig config = appConfigStore.getAppConfig();

        // Resolve DataForm and element
        DataForm dataForm = config.getDataForms().get(dataFormCode);
        if (dataForm == null) {
            throw new IllegalArgumentException("DataForm not found: " + dataFormCode);
        }

        DataFormElement element = dataForm.getElements().get(elementCode);
        if (element == null || element.getType() != DataFormElementType.GRID) {
            throw new IllegalArgumentException("GRID element not found: " + elementCode);
        }

        if (element.getEntityProviderRef() == null) {
            throw new IllegalStateException("GRID element '" + elementCode + "' has no entityProviderRef");
        }

        EntityProvider provider = config.getEntityProviders().get(element.getEntityProviderRef());
        if (provider == null || provider.getEntityType() == null) {
            throw new IllegalStateException("EntityProvider not found: " + element.getEntityProviderRef());
        }

        // Build transient editor entity from form state
        Class<?> editorEntityClass = resolveClass(dataForm.getEntity().getFqcn());
        Object editorEntity = null;
        if (entityId != null && formState != null && !formState.isEmpty()) {
            editorEntity = editorEntityBuilder.buildFromFormState(editorEntityClass, entityId, formState);
        } else if (entityId != null) {
            editorEntity = entityManager.find(editorEntityClass, entityId);
        }

        // Build expression context
        ExpressionContext context = new ExpressionContext();
        context.put("editor", editorEntity);
        context.put("formState", formState != null ? formState : Map.of());

        // Execute query with dynamic filter resolution
        Class<?> gridEntityClass = resolveClass(provider.getEntityType().getFqcn());
        int offset = page * pageSize;
        FilterExecutor.PagedResult paged = filterExecutor.executePagedQuery(
                provider, gridEntityClass, offset, pageSize, context);

        // Build column renderers
        Map<String, Template> columnRenderers = new LinkedHashMap<>();
        for (TableColumn col : element.getTableColumns()) {
            if (col.getEntityRendererRef() != null) {
                EntityRenderer renderer = config.getEntityRenderers().get(col.getEntityRendererRef());
                if (renderer != null && renderer.getTemplate() != null) {
                    columnRenderers.put(col.getKey(), Mustache.compiler().compile(renderer.getTemplate()));
                }
            }
        }

        // Map results to rows
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entity : paged.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", getId(entity));
            for (TableColumn col : element.getTableColumns()) {
                String key = col.getKey();
                Object value = getProperty(entity, key);

                if (value != null && isJpaEntity(value.getClass())) {
                    Template template = columnRenderers.get(key);
                    if (template != null) {
                        row.put(key, template.execute(buildEntityContext(value)));
                    } else {
                        row.put(key, getId(value));
                    }
                } else {
                    row.put(key, value);
                }
            }
            rows.add(row);
        }

        return new GridPagedResult(rows, paged.totalCount(), page, pageSize);
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
                try { return method.invoke(entity); } catch (ReflectiveOperationException e) { return null; }
            }
        }
        return null;
    }

    private Long getId(Object entity) {
        try { return (Long) entity.getClass().getMethod("getId").invoke(entity); }
        catch (ReflectiveOperationException e) { return null; }
    }

    private boolean isJpaEntity(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    private Class<?> resolveEntityClass(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.persistence.Entity.class)) return current;
            current = current.getSuperclass();
        }
        return entity.getClass();
    }

    private Class<?> resolveClass(String fqcn) {
        try { return Class.forName(fqcn); }
        catch (ClassNotFoundException e) { throw new IllegalArgumentException("Entity class not found: " + fqcn, e); }
    }
}
