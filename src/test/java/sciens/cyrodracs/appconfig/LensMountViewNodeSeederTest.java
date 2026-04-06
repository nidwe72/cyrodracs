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
 * Seeds an EntityProvider "allCameraLensMounts" and a ViewNode "cameraLensMounts"
 * (ENTITY_LIST) into the main navigation tree.
 */
@SpringBootTest
class LensMountViewNodeSeederTest {

    @Autowired AppConfigObjectRepository objectRepo;
    @Autowired AppConfigTypeRepository typeRepo;

    @Test
    void seedLensMountViewNode() {
        AppConfigObjectEntity root = objectRepo.findAll().stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElseThrow();

        // ── 1. EntityProvider: allCameraLensMounts ──
        AppConfigObjectEntity provider = ensureChild(root, "EntityProvider", "allCameraLensMounts");
        ensureChild(provider, "EntityProviderEntityType", "CAMERA_LENS_MOUNT", "CAMERA_LENS_MOUNT");
        AppConfigObjectEntity sortField = ensureChild(provider, "SortField", "sortByName");
        ensureChild(sortField, "SortFieldField", "name");
        ensureChild(sortField, "SortDirection", "ASC", "ASC");

        // ── 2. EntityRenderer: lensMountCaption (ensure exists with template) ──
        AppConfigObjectEntity lensMountRenderer = ensureChild(root, "EntityRenderer", "lensMountCaption");
        ensureChild(lensMountRenderer, "EntityRendererEntityType", "CAMERA_LENS_MOUNT", "CAMERA_LENS_MOUNT");
        ensureChild(lensMountRenderer, "EntityRendererTemplate", "{{name}}");

        // ── 3. ViewNode: cameraLensMounts (ENTITY_LIST) ──
        AppConfigObjectEntity viewNode = ensureChild(root, "ViewNode", "cameraLensMounts");
        ensureChild(viewNode, "ViewNodeType", "ENTITY_LIST", "ENTITY_LIST");
        ensureChild(viewNode, "ViewNodeLabel", "Camera Lens Mounts");
        ensureChild(viewNode, "ViewNodeProviderRef", "allCameraLensMounts");
        ensureChild(viewNode, "ViewNodeDataFormRef", "cameraLensMount");

        // TableColumns
        AppConfigObjectEntity colName = ensureChild(viewNode, "TableColumn", "col_name");
        ensureChild(colName, "TableColumnKey", "name");
        ensureChild(colName, "TableColumnHeader", "Name");

        AppConfigObjectEntity colProducer = ensureChild(viewNode, "TableColumn", "col_producer");
        ensureChild(colProducer, "TableColumnKey", "producer");
        ensureChild(colProducer, "TableColumnHeader", "Creator");
        ensureChild(colProducer, "TableColumnRendererRef", "producerCaption");

        // ── Verify ──
        assertNotNull(viewNode.getId());
        assertNotNull(provider.getId());

        sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder treeBuilder =
                new sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder(objectRepo);
        AppConfig config = treeBuilder.buildTree();

        assertNotNull(config.getEntityProviders().get("allCameraLensMounts"));
        ViewNode vn = config.getViewTree().get("cameraLensMounts");
        assertNotNull(vn);
        assertEquals(ViewNodeType.ENTITY_LIST, vn.getType());
        assertEquals("Camera Lens Mounts", vn.getLabel());
        assertEquals(2, vn.getTableColumns().size());
    }

    private AppConfigObjectEntity ensureChild(AppConfigObjectEntity parent, String typeCode,
                                               String code) {
        return ensureChild(parent, typeCode, code, null);
    }

    private AppConfigObjectEntity ensureChild(AppConfigObjectEntity parent, String typeCode,
                                               String code, String enumValue) {
        AppConfigTypeEntity type = typeRepo.findByCode(typeCode)
                .orElseThrow(() -> new IllegalStateException("Type not found: " + typeCode));

        List<AppConfigObjectEntity> existing = objectRepo.findByParentObject(parent);
        for (AppConfigObjectEntity obj : existing) {
            if (obj.getType().getCode().equals(typeCode) && obj.getCode().equals(code)) {
                return obj;
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
