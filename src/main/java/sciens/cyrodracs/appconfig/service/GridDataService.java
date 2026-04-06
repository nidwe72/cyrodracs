package sciens.cyrodracs.appconfig.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.expression.EditorEntityBuilder;
import sciens.cyrodracs.expression.ExpressionContext;

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
    private final ColumnRenderer columnRenderer;

    public GridDataService(AppConfigStore appConfigStore, EntityManager entityManager,
                           FilterExecutor filterExecutor, EditorEntityBuilder editorEntityBuilder,
                           ColumnRenderer columnRenderer) {
        this.appConfigStore = appConfigStore;
        this.entityManager = entityManager;
        this.filterExecutor = filterExecutor;
        this.editorEntityBuilder = editorEntityBuilder;
        this.columnRenderer = columnRenderer;
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
            row.put("id", columnRenderer.getId(entity));
            for (TableColumn col : element.getTableColumns()) {
                row.put(col.getKey(), columnRenderer.resolveAndRender(
                        entity, col.getKey(), columnRenderers.get(col.getKey())));
            }
            rows.add(row);
        }

        return new GridPagedResult(rows, paged.totalCount(), page, pageSize);
    }

    private Class<?> resolveClass(String fqcn) {
        try { return Class.forName(fqcn); }
        catch (ClassNotFoundException e) { throw new IllegalArgumentException("Entity class not found: " + fqcn, e); }
    }
}
