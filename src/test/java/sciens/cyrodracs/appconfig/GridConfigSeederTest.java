package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seeds the AppConfig objects needed for the GRID use case:
 * - Expression "producerMountFilter" (INJECTABLE_CLASS, FILTER)
 * - EntityProvider "mountsForCurrentProducer" with filterInjectableRef
 * - EntityRenderer "lensMountCaption" and "producerCaption"
 * - DataFormElement "lensMountMappings" (GRID) with tableColumns on cameraProducer
 */
@SpringBootTest
class GridConfigSeederTest {

    @Autowired AppConfigObjectRepository objectRepo;
    @Autowired AppConfigTypeRepository typeRepo;

    @Test
    void seedGridConfig() {
        // Find root AppConfig object
        AppConfigObjectEntity root = objectRepo.findAll().stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No root AppConfig object"));

        // ── 1. Expression: producerMountFilter ──
        AppConfigObjectEntity expr = ensureChild(root, "Expression", "producerMountFilter");
        ensureChild(expr, "ExpressionType", "INJECTABLE_CLASS", "INJECTABLE_CLASS");
        ensureChild(expr, "InjectableBaseClass", "FILTER", "FILTER");
        ensureChild(expr, "ExpressionBody", """
                @Override
                public void execute() {
                    CameraProducer p = (CameraProducer) getInjectionContext().getEditorEntity();
                    if (p == null) {
                        setResult(null);
                        return;
                    }
                    setResult(
                        comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId())
                    );
                }""");
        ensureChild(expr, "ExpressionDescription", "Restricts CameraLensMount2CameraProducer to the currently edited producer");

        // ── 2. EntityRenderer: lensMountCaption ──
        AppConfigObjectEntity lensMountRenderer = ensureChild(root, "EntityRenderer", "lensMountCaption");
        ensureChild(lensMountRenderer, "EntityRendererEntityType", "CAMERA_LENS_MOUNT", "CAMERA_LENS_MOUNT");
        ensureChild(lensMountRenderer, "EntityRendererTemplate", "{{name}}");

        // ── 3. Ensure producerCaption renderer has entityType + template ──
        AppConfigObjectEntity producerRenderer = ensureChild(root, "EntityRenderer", "producerCaption");
        ensureChild(producerRenderer, "EntityRendererEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
        ensureChild(producerRenderer, "EntityRendererTemplate", "{{name}}");

        // ── 4. EntityProvider: mountsForCurrentProducer ──
        AppConfigObjectEntity provider = ensureChild(root, "EntityProvider", "mountsForCurrentProducer");
        ensureChild(provider, "EntityProviderEntityType", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");
        ensureChild(provider, "FilterInjectableRef", "producerMountFilter");

        // SortField: cameraLensMount.name ASC
        AppConfigObjectEntity sortField = ensureChild(provider, "SortField", "sortByMountName");
        ensureChild(sortField, "SortFieldField", "cameraLensMount.name");
        ensureChild(sortField, "SortDirection", "ASC", "ASC");

        // ── 5. DataFormElement: lensMountMappings (GRID) on the existing cameraProducer form ──
        // Find the existing cameraProducer DataForm (NOT cameraProducer)
        AppConfigObjectEntity producerForm = objectRepo.findAll().stream()
                .filter(o -> "DataForm".equals(o.getType().getCode()) && "cameraProducer".equals(o.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "DataForm 'cameraProducer' not found — it should already exist in the DB"));

        // GRID element
        AppConfigObjectEntity gridElement = ensureChild(producerForm, "DataFormElement", "lensMountMappings");
        ensureChild(gridElement, "DataFormElementType", "GRID", "GRID");
        ensureChild(gridElement, "EntityProviderRef", "mountsForCurrentProducer");

        // TableColumns for the GRID
        AppConfigObjectEntity colMount = ensureChild(gridElement, "GridTableColumn", "col_mount");
        ensureChild(colMount, "GridTableColumnKey", "cameraLensMount");
        ensureChild(colMount, "GridTableColumnHeader", "Lens Mount");
        ensureChild(colMount, "GridTableColumnRendererRef", "lensMountCaption");

        AppConfigObjectEntity colProducer = ensureChild(gridElement, "GridTableColumn", "col_mountProducer");
        ensureChild(colProducer, "GridTableColumnKey", "cameraProducer");
        ensureChild(colProducer, "GridTableColumnHeader", "Producer");
        ensureChild(colProducer, "GridTableColumnRendererRef", "producerCaption");

        // ── Verify ──
        assertNotNull(expr.getId());
        assertNotNull(provider.getId());
        assertNotNull(gridElement.getId());

        // Verify tree builder can read it
        sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder treeBuilder =
                new sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder(objectRepo);
        AppConfig config = treeBuilder.buildTree();
        assertNotNull(config.getExpressions().get("producerMountFilter"));
        assertEquals(ExpressionType.INJECTABLE_CLASS, config.getExpressions().get("producerMountFilter").getType());
        assertEquals(InjectableBaseClass.FILTER, config.getExpressions().get("producerMountFilter").getBaseClass());
        assertNotNull(config.getEntityProviders().get("mountsForCurrentProducer"));
        assertEquals("producerMountFilter", config.getEntityProviders().get("mountsForCurrentProducer").getFilterInjectableRef());

        DataForm form = config.getDataForms().get("cameraProducer");
        assertNotNull(form);
        DataFormElement grid = form.getElements().get("lensMountMappings");
        assertNotNull(grid);
        assertEquals(DataFormElementType.GRID, grid.getType());
        assertEquals(2, grid.getTableColumns().size());
    }

    private AppConfigObjectEntity ensureChild(AppConfigObjectEntity parent, String typeCode, String code) {
        return ensureChild(parent, typeCode, code, null);
    }

    private AppConfigObjectEntity ensureChild(AppConfigObjectEntity parent, String typeCode,
                                               String code, String enumValue) {
        AppConfigTypeEntity type = typeRepo.findByCode(typeCode)
                .orElseThrow(() -> new IllegalStateException("Type not found: " + typeCode));

        List<AppConfigObjectEntity> existing = objectRepo.findByParentObject(parent);
        for (AppConfigObjectEntity obj : existing) {
            if (obj.getType().getCode().equals(typeCode)) {
                // For non-collection types (enum values, string values), match by type only
                // and update the code/enumValue if different
                if (!type.isCollection()) {
                    if (!code.equals(obj.getCode()) || !java.util.Objects.equals(enumValue, obj.getEnumValue())) {
                        obj.setCode(code);
                        obj.setEnumValue(enumValue);
                        return objectRepo.save(obj);
                    }
                    return obj;
                }
                // For collection types, match by type + code
                if (obj.getCode().equals(code)) {
                    return obj;
                }
            }
        }

        AppConfigObjectEntity obj = new AppConfigObjectEntity();
        obj.setType(type);
        obj.setCode(code);
        obj.setParentObject(parent);
        obj.setEnumValue(enumValue);
        return objectRepo.save(obj);
    }
}
