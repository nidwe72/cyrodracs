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
 * Seeds configuration for the Camera DataForm with filtered ENTITY_SELECT:
 *
 * - Expression "cameraMountFilter" (FilterInjectable filtering CameraLensMount2CameraProducer by Camera.producer)
 * - EntityProvider "mountsForCamera" with filterInjectableRef
 * - EntityRenderer "mountMappingCaption" with template {{cameraLensMount.name}} ({{cameraLensMount.producer.name}})
 * - DataForm "camera" with elements: name, releaseYear, producer (ENTITY_SELECT), cameraLensMount2CameraProducer (ENTITY_SELECT)
 */
@SpringBootTest
class CameraFormSeederTest {

    @Autowired AppConfigObjectRepository objectRepo;
    @Autowired AppConfigTypeRepository typeRepo;

    @Test
    void seedCameraFormConfig() {
        AppConfigObjectEntity root = objectRepo.findAll().stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No root AppConfig object"));

        // ── 1. Expression: cameraMountFilter ──
        AppConfigObjectEntity expr = ensureChild(root, "Expression", "cameraMountFilter");
        ensureChild(expr, "ExpressionType", "INJECTABLE_CLASS", "INJECTABLE_CLASS");
        ensureChild(expr, "InjectableBaseClass", "FILTER", "FILTER");
        ensureChild(expr, "ExpressionBody", """
                @Override
                public void execute() {
                    Camera c = (Camera) getInjectionContext().getEditorEntity();
                    if (c == null || c.getProducer() == null) {
                        setResult(null);
                        return;
                    }
                    setResult(
                        comparison("cameraProducer.id", FilterOperator.EQUALS, c.getProducer().getId())
                    );
                }""");
        ensureChild(expr, "ExpressionDescription",
                "Restricts CameraLensMount2CameraProducer to the Camera's selected producer");

        // ── 2. EntityRenderer: mountMappingCaption ──
        AppConfigObjectEntity renderer = ensureChild(root, "EntityRenderer", "mountMappingCaption");
        ensureChild(renderer, "EntityRendererEntityType",
                "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");
        ensureChild(renderer, "EntityRendererTemplate",
                "{{cameraLensMount.name}} ({{cameraLensMount.producer.name}})");

        // ── 3. EntityProvider: mountsForCamera ──
        AppConfigObjectEntity provider = ensureChild(root, "EntityProvider", "mountsForCamera");
        ensureChild(provider, "EntityProviderEntityType",
                "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER", "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER");
        ensureChild(provider, "FilterInjectableRef", "cameraMountFilter");
        AppConfigObjectEntity sortField = ensureChild(provider, "SortField", "sortByMountName");
        ensureChild(sortField, "SortFieldField", "cameraLensMount.name");
        ensureChild(sortField, "SortDirection", "ASC", "ASC");

        // ── 4. Ensure EntityProvider "allCameraProducers" exists ──
        objectRepo.findAll().stream()
                .filter(o -> "EntityProvider".equals(o.getType().getCode())
                        && "allCameraProducers".equals(o.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    AppConfigObjectEntity p = ensureChild(root, "EntityProvider", "allCameraProducers");
                    ensureChild(p, "EntityProviderEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
                    return p;
                });

        // ── 5. Ensure EntityRenderer "producerCaption" exists ──
        AppConfigObjectEntity prodRenderer = ensureChild(root, "EntityRenderer", "producerCaption");
        ensureChild(prodRenderer, "EntityRendererEntityType", "CAMERA_PRODUCER", "CAMERA_PRODUCER");
        ensureChild(prodRenderer, "EntityRendererTemplate", "{{name}}");

        // ── 6. DataForm: camera ──
        AppConfigObjectEntity cameraForm = ensureChild(root, "DataForm", "camera");
        ensureChild(cameraForm, "DataFormEntityType", "CAMERA", "CAMERA");

        // Element: name (INPUT_STRING)
        AppConfigObjectEntity elName = ensureChild(cameraForm, "DataFormElement", "name");
        ensureChild(elName, "DataFormElementType", "INPUT_STRING", "INPUT_STRING");
        ensureChild(elName, "DataBinding", "name");
        ensureChild(elName, "Mandatory", "true");

        // Element: releaseYear (DATE_PICKER__YEAR_MONTH)
        AppConfigObjectEntity elYear = ensureChild(cameraForm, "DataFormElement", "releaseYear");
        ensureChild(elYear, "DataFormElementType", "DATE_PICKER__YEAR_MONTH", "DATE_PICKER__YEAR_MONTH");
        ensureChild(elYear, "DataBinding", "releaseYear");

        // Element: producer (ENTITY_SELECT)
        AppConfigObjectEntity elProducer = ensureChild(cameraForm, "DataFormElement", "producer");
        ensureChild(elProducer, "DataFormElementType", "ENTITY_SELECT", "ENTITY_SELECT");
        ensureChild(elProducer, "DataBinding", "producer");
        ensureChild(elProducer, "EntityProviderRef", "allCameraProducers");
        ensureChild(elProducer, "EntityRendererRef", "producerCaption");
        ensureChild(elProducer, "Mandatory", "true");

        // Element: cameraLensMount2CameraProducer (ENTITY_SELECT, filtered, reloads when producer changes)
        AppConfigObjectEntity elMount = ensureChild(cameraForm, "DataFormElement", "cameraLensMount2CameraProducer");
        ensureChild(elMount, "DataFormElementType", "ENTITY_SELECT", "ENTITY_SELECT");
        ensureChild(elMount, "DataBinding", "cameraLensMount2CameraProducer");
        ensureChild(elMount, "EntityProviderRef", "mountsForCamera");
        ensureChild(elMount, "EntityRendererRef", "mountMappingCaption");
        ensureChild(elMount, "ReloadOnChangeOf", "producer");
        // Not mandatory — fixed-lens cameras have no lens mount

        // ── Verify tree build ──
        sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder treeBuilder =
                new sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder(objectRepo);
        AppConfig config = treeBuilder.buildTree();

        // Expression
        Expression exprModel = config.getExpressions().get("cameraMountFilter");
        assertNotNull(exprModel, "Expression 'cameraMountFilter' should exist");
        assertEquals(ExpressionType.INJECTABLE_CLASS, exprModel.getType());
        assertEquals(InjectableBaseClass.FILTER, exprModel.getBaseClass());

        // EntityProvider
        EntityProvider providerModel = config.getEntityProviders().get("mountsForCamera");
        assertNotNull(providerModel, "EntityProvider 'mountsForCamera' should exist");
        assertEquals("cameraMountFilter", providerModel.getFilterInjectableRef());
        assertEquals(DataFormEntityType.CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER, providerModel.getEntityType());

        // EntityRenderer
        EntityRenderer rendererModel = config.getEntityRenderers().get("mountMappingCaption");
        assertNotNull(rendererModel, "EntityRenderer 'mountMappingCaption' should exist");
        assertEquals("{{cameraLensMount.name}} ({{cameraLensMount.producer.name}})", rendererModel.getTemplate());

        // DataForm
        DataForm form = config.getDataForms().get("camera");
        assertNotNull(form, "DataForm 'camera' should exist");
        assertEquals(DataFormEntityType.CAMERA, form.getEntity());
        assertEquals(4, form.getElements().size());

        // Element: name
        DataFormElement nameEl = form.getElements().get("name");
        assertNotNull(nameEl);
        assertEquals(DataFormElementType.INPUT_STRING, nameEl.getType());
        assertEquals("name", nameEl.getDataBinding());
        assertTrue(nameEl.isMandatory(), "name should be mandatory");

        // Element: producer
        DataFormElement producerEl = form.getElements().get("producer");
        assertNotNull(producerEl);
        assertEquals(DataFormElementType.ENTITY_SELECT, producerEl.getType());
        assertEquals("allCameraProducers", producerEl.getEntityProviderRef());
        assertTrue(producerEl.isMandatory(), "producer should be mandatory");

        // Element: cameraLensMount2CameraProducer
        DataFormElement mountEl = form.getElements().get("cameraLensMount2CameraProducer");
        assertNotNull(mountEl);
        assertEquals(DataFormElementType.ENTITY_SELECT, mountEl.getType());
        assertEquals("mountsForCamera", mountEl.getEntityProviderRef());
        assertEquals("mountMappingCaption", mountEl.getEntityRendererRef());
        assertFalse(mountEl.isMandatory(), "lens mount should not be mandatory");
        assertEquals(List.of("producer"), mountEl.getReloadOnChangeOf(),
                "lens mount should reload when producer changes");
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
