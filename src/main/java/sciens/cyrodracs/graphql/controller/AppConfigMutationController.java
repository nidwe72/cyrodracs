package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.service.AppConfigMutationService;
import sciens.cyrodracs.appconfig.web.AddNodeRequest;
import sciens.cyrodracs.appconfig.web.UpdateNodeRequest;

import java.util.Map;

@Controller
public class AppConfigMutationController {

    private final AppConfigMutationService mutationService;
    private final AppConfigStore store;
    private final AppConfigObjectRepository objectRepo;

    public AppConfigMutationController(AppConfigMutationService mutationService,
                                        AppConfigStore store,
                                        AppConfigObjectRepository objectRepo) {
        this.mutationService = mutationService;
        this.store = store;
        this.objectRepo = objectRepo;
    }

    private AppConfig reloadAndReturn() {
        store.reload();
        return store.getAppConfig();
    }

    // ── Fine-grained node operations ────────────────────────────

    @MutationMapping
    public AppConfig addAppConfigNode(@Argument Map<String, Object> input) {
        mutationService.addNode(new AddNodeRequest(
                input.get("parentObjectId") != null ? ((Number) input.get("parentObjectId")).longValue() : null,
                (String) input.get("typeCode"),
                (String) input.get("code"),
                (String) input.get("enumValue")
        ));
        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig updateAppConfigNode(@Argument Long id, @Argument Map<String, Object> input) {
        mutationService.updateNode(id, new UpdateNodeRequest(
                (String) input.get("code"),
                (String) input.get("enumValue")
        ));
        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig copyAppConfigNode(@Argument Long id, @Argument String newCode) {
        mutationService.copyNode(id, newCode);
        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig deleteAppConfigNode(@Argument Long id) {
        mutationService.deleteNode(id);
        return reloadAndReturn();
    }

    // ── Coarse-grained operations ───────────────────────────────

    @MutationMapping
    public AppConfig addDataFormElement(@Argument Map<String, Object> input) {
        Long parentFormId = ((Number) input.get("parentFormId")).longValue();
        String code = (String) input.get("code");
        String type = (String) input.get("type");

        mutationService.addNode(new AddNodeRequest(parentFormId, "DataFormElement", code, null));
        store.reload();

        if (type != null) {
            var parent = objectRepo.findById(parentFormId).orElseThrow();
            var element = objectRepo.findByParentObject(parent).stream()
                    .filter(e -> "DataFormElement".equals(e.getType().getCode()) && code.equals(e.getCode()))
                    .findFirst().orElse(null);
            if (element != null) {
                mutationService.addNode(new AddNodeRequest(element.getId(), "DataFormElementType", code + "_type", type));
                store.reload();
            }
        }
        return store.getAppConfig();
    }

    @MutationMapping
    public AppConfig updateDataFormElementFull(@Argument Map<String, Object> input) {
        Long elementId = ((Number) input.get("elementId")).longValue();
        String code = (String) input.get("code");
        String type = (String) input.get("type");
        String dataBinding = (String) input.get("dataBinding");
        String entityProviderRef = (String) input.get("entityProviderRef");
        String entityRendererRef = (String) input.get("entityRendererRef");

        String elementCode = code != null ? code : objectRepo.findById(elementId).orElseThrow().getCode();

        if (code != null) mutationService.updateNode(elementId, new UpdateNodeRequest(code, null));
        if (type != null) upsertChild(elementId, "DataFormElementType", elementCode + "_type", type);
        if (dataBinding != null) upsertChild(elementId, "DataBinding", dataBinding, null);
        if (entityProviderRef != null) upsertChild(elementId, "EntityProviderRef", entityProviderRef, null);
        if (entityRendererRef != null) upsertChild(elementId, "EntityRendererRef", entityRendererRef, null);

        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig updateDataForm(@Argument Map<String, Object> input) {
        Long formId = ((Number) input.get("formId")).longValue();
        String code = (String) input.get("code");
        String entity = (String) input.get("entity");

        String formCode = code != null ? code : objectRepo.findById(formId).orElseThrow().getCode();

        if (code != null) mutationService.updateNode(formId, new UpdateNodeRequest(code, null));
        if (entity != null) upsertChild(formId, "DataFormEntityType", formCode + "_entity", entity);

        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig updateEntityProvider(@Argument Map<String, Object> input) {
        Long providerId = ((Number) input.get("providerId")).longValue();
        String code = (String) input.get("code");
        String entityType = (String) input.get("entityType");

        String providerCode = code != null ? code : objectRepo.findById(providerId).orElseThrow().getCode();

        if (code != null) mutationService.updateNode(providerId, new UpdateNodeRequest(code, null));
        if (entityType != null) upsertChild(providerId, "EntityProviderEntityType", providerCode + "_entityType", entityType);

        return reloadAndReturn();
    }

    @MutationMapping
    public AppConfig updateEntityRenderer(@Argument Map<String, Object> input) {
        Long rendererId = ((Number) input.get("rendererId")).longValue();
        String code = (String) input.get("code");
        String entityType = (String) input.get("entityType");
        String template = (String) input.get("template");

        String rendererCode = code != null ? code : objectRepo.findById(rendererId).orElseThrow().getCode();

        if (code != null) mutationService.updateNode(rendererId, new UpdateNodeRequest(code, null));
        if (entityType != null) upsertChild(rendererId, "EntityRendererEntityType", rendererCode + "_entityType", entityType);
        if (template != null) upsertChild(rendererId, "EntityRendererTemplate", template, null);

        return reloadAndReturn();
    }

    // ── Helper ──────────────────────────────────────────────────

    private void upsertChild(Long parentId, String typeCode, String code, String enumValue) {
        var parent = objectRepo.findById(parentId).orElseThrow();
        var existing = objectRepo.findByParentObject(parent).stream()
                .filter(e -> typeCode.equals(e.getType().getCode()))
                .findFirst().orElse(null);

        if (existing != null) {
            String updateCode = enumValue == null ? code : null;
            mutationService.updateNode(existing.getId(), new UpdateNodeRequest(updateCode, enumValue));
        } else {
            mutationService.addNode(new AddNodeRequest(parentId, typeCode, code, enumValue));
        }
    }
}
