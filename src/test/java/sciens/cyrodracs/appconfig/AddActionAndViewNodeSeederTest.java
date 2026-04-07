package sciens.cyrodracs.appconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seeds configuration for Phases 1+2:
 *
 * Phase 1 (G8):
 * - EntityRenderer "lensMountWithProducerCaption"
 * - EntityProvider "allLensMountMappings"
 * - DataForm "lensMountMappingForm" with two ENTITY_SELECT elements
 * - ViewNode "lensMountMappings" (ENTITY_LIST) in the main tree
 *
 * Phase 2 (G6):
 * - AddAction on existing GRID element "lensMountMappings" on cameraProducer form
 *   with ContextBinding: target="cameraProducer", source="ENTITY"
 */
@SpringBootTest
class AddActionAndViewNodeSeederTest {

    @Autowired AppConfigObjectRepository objectRepo;
    @Autowired AppConfigTypeRepository typeRepo;

    @Test
    void seedPhase1And2() {
        AppConfigObjectEntity root = objectRepo.findAll().stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No root AppConfig object"));

        // ── Phase 1: Foundation Config ──

        // 1. EntityRenderer: lensMountWithProducerCaption
        AppConfigObjectEntity lmRenderer = ensureChild(root, "EntityRenderer", "lensMountWithProducerCaption");
        ensureChild(lmRenderer, "EntityRendererEntityType", "CAMERA_LENS_MOUNT", "CAMERA_LENS_MOUNT");
        ensureChild(lmRenderer, "EntityRendererTemplate", "{{name}} ({{producer.name}})");

        // 2. EntityProvider: allLensMountMappings (unfiltered, for standalone ViewNode)
        AppConfigObjectEntity allMappingsProvider = ensureChild(root, "EntityProvider", "allLensMountMappings");
        ensureChild(allMappingsProvider, "EntityProviderEntityType",
                "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");
        AppConfigObjectEntity sortField = ensureChild(allMappingsProvider, "SortField", "sortByProducerName");
        ensureChild(sortField, "SortFieldField", "cameraProducer.name");
        ensureChild(sortField, "SortDirection", "ASC", "ASC");

        // 3. DataForm: lensMountMappingForm
        AppConfigObjectEntity mappingForm = ensureChild(root, "DataForm", "lensMountMappingForm");
        ensureChild(mappingForm, "DataFormEntityType",
                "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");

        // Element: cameraProducer (ENTITY_SELECT)
        AppConfigObjectEntity elProducer = ensureChild(mappingForm, "DataFormElement", "cameraProducer");
        ensureChild(elProducer, "DataFormElementType", "ENTITY_SELECT", "ENTITY_SELECT");
        ensureChild(elProducer, "DataBinding", "cameraProducer");
        ensureChild(elProducer, "EntityProviderRef", "allCameraProducers");
        ensureChild(elProducer, "EntityRendererRef", "producerCaption");

        // Element: cameraLensMount (ENTITY_SELECT)
        AppConfigObjectEntity elMount = ensureChild(mappingForm, "DataFormElement", "cameraLensMount");
        ensureChild(elMount, "DataFormElementType", "ENTITY_SELECT", "ENTITY_SELECT");
        ensureChild(elMount, "DataBinding", "cameraLensMount");
        ensureChild(elMount, "EntityProviderRef", "allCameraLensMounts");
        ensureChild(elMount, "EntityRendererRef", "lensMountWithProducerCaption");

        // 4. Ensure EntityProvider "mountsForCurrentProducer" exists (for the GRID)
        AppConfigObjectEntity mountsProvider = objectRepo.findAll().stream()
                .filter(o -> "EntityProvider".equals(o.getType().getCode())
                        && "mountsForCurrentProducer".equals(o.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    AppConfigObjectEntity p = ensureChild(root, "EntityProvider", "mountsForCurrentProducer");
                    ensureChild(p, "EntityProviderEntityType",
                            "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");
                    return p;
                });

        // 5. Ensure EntityProvider "allCameraProducers" exists (for ENTITY_SELECT in child form)
        objectRepo.findAll().stream()
                .filter(o -> "EntityProvider".equals(o.getType().getCode())
                        && "allCameraProducers".equals(o.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    AppConfigObjectEntity p = ensureChild(root, "EntityProvider", "allCameraProducers");
                    ensureChild(p, "EntityProviderEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
                    return p;
                });

        // 6. Ensure EntityRenderer "producerCaption" exists
        AppConfigObjectEntity prodRenderer = ensureChild(root, "EntityRenderer", "producerCaption");
        ensureChild(prodRenderer, "EntityRendererEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
        ensureChild(prodRenderer, "EntityRendererTemplate", "{{name}}");

        // 7. ViewNode: lensMountMappings (ENTITY_LIST)
        AppConfigObjectEntity viewNode = ensureChild(root, "ViewNode", "lensMountMappings");
        ensureChild(viewNode, "ViewNodeType", "ENTITY_LIST", "ENTITY_LIST");
        ensureChild(viewNode, "ViewNodeLabel", "Lens Mount Assignments");
        ensureChild(viewNode, "ViewNodeProviderRef", "allLensMountMappings");
        ensureChild(viewNode, "ViewNodeDataFormRef", "lensMountMappingForm");

        // TableColumns for the ViewNode
        AppConfigObjectEntity colProducer = ensureChild(viewNode, "TableColumn", "col_producer");
        ensureChild(colProducer, "TableColumnKey", "cameraProducer");
        ensureChild(colProducer, "TableColumnHeader", "Producer");
        ensureChild(colProducer, "TableColumnRendererRef", "producerCaption");

        AppConfigObjectEntity colMount = ensureChild(viewNode, "TableColumn", "col_mount");
        ensureChild(colMount, "TableColumnKey", "cameraLensMount");
        ensureChild(colMount, "TableColumnHeader", "Lens Mount");
        ensureChild(colMount, "TableColumnRendererRef", "lensMountWithProducerCaption");

        // ── Phase 2: AddAction on existing GRID element ──

        // Find (or create) the cameraProducer DataForm and its GRID element
        AppConfigObjectEntity producerForm = objectRepo.findAll().stream()
                .filter(o -> "DataForm".equals(o.getType().getCode()) && "cameraProducer".equals(o.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    // Self-contained: create the DataForm if it doesn't exist
                    AppConfigObjectEntity form = ensureChild(root, "DataForm", "cameraProducer");
                    ensureChild(form, "DataFormEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
                    return form;
                });

        // Ensure the GRID element exists
        AppConfigObjectEntity gridElement = objectRepo.findByParentObject(producerForm).stream()
                .filter(o -> "DataFormElement".equals(o.getType().getCode())
                        && "lensMountMappings".equals(o.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    AppConfigObjectEntity grid = ensureChild(producerForm, "DataFormElement", "lensMountMappings");
                    ensureChild(grid, "DataFormElementType", "GRID", "GRID");
                    ensureChild(grid, "EntityProviderRef", "mountsForCurrentProducer");
                    return grid;
                });

        // AddAction on the GRID element
        AppConfigObjectEntity addAction = ensureChild(gridElement, "AddAction", "addLensMountMapping");
        ensureChild(addAction, "AddActionTarget", "lensMountMappingForm");
        ensureChild(addAction, "AddActionLabel", "LensMount Assignment");

        // ContextBinding: cameraProducer → ENTITY
        AppConfigObjectEntity binding = ensureChild(addAction, "ContextBinding", "cameraProducerBinding");
        ensureChild(binding, "ContextBindingTarget", "cameraProducer");
        ensureChild(binding, "ContextBindingSource", "ENTITY");

        // ── Verify Phase 1 ──
        sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder treeBuilder =
                new sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder(objectRepo);
        AppConfig config = treeBuilder.buildTree();

        // Renderer
        EntityRenderer renderer = config.getEntityRenderers().get("lensMountWithProducerCaption");
        assertNotNull(renderer);
        assertEquals("{{name}} ({{producer.name}})", renderer.getTemplate());

        // EntityProvider
        assertNotNull(config.getEntityProviders().get("allLensMountMappings"));

        // DataForm
        DataForm form = config.getDataForms().get("lensMountMappingForm");
        assertNotNull(form);
        assertEquals(DataFormEntityType.CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER, form.getEntity());
        assertEquals(2, form.getElements().size());
        assertNotNull(form.getElements().get("cameraProducer"));
        assertNotNull(form.getElements().get("cameraLensMount"));
        assertEquals(DataFormElementType.ENTITY_SELECT, form.getElements().get("cameraProducer").getType());
        assertEquals("allCameraProducers", form.getElements().get("cameraProducer").getEntityProviderRef());

        // ViewNode
        ViewNode vn = config.getViewTree().get("lensMountMappings");
        assertNotNull(vn);
        assertEquals(ViewNodeType.ENTITY_LIST, vn.getType());
        assertEquals("Lens Mount Assignments", vn.getLabel());
        assertEquals("allLensMountMappings", vn.getEntityProviderRef());
        assertEquals("lensMountMappingForm", vn.getDataFormRef());
        assertEquals(2, vn.getTableColumns().size());

        // ── Verify Phase 2 ──
        DataForm producerFormModel = config.getDataForms().get("cameraProducer");
        assertNotNull(producerFormModel);
        DataFormElement grid = producerFormModel.getElements().get("lensMountMappings");
        assertNotNull(grid);
        assertEquals(DataFormElementType.GRID, grid.getType());

        AddAction action = grid.getAddAction();
        assertNotNull(action, "GRID element should have an addAction");
        assertEquals("lensMountMappingForm", action.getTargetDataFormRef());
        assertEquals("LensMount Assignment", action.getChildLabel());
        assertEquals(1, action.getContextBindings().size());

        ContextBinding cb = action.getContextBindings().get(0);
        assertEquals("cameraProducer", cb.getTarget());
        assertEquals("ENTITY", cb.getSource());
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
                if (!type.isCollection()) {
                    if (!code.equals(obj.getCode()) || !java.util.Objects.equals(enumValue, obj.getEnumValue())) {
                        obj.setCode(code);
                        obj.setEnumValue(enumValue);
                        return objectRepo.save(obj);
                    }
                    return obj;
                }
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
