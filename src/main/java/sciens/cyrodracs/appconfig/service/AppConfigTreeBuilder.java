package sciens.cyrodracs.appconfig.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.DataFormEntityType;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppConfigTreeBuilder {

    private final AppConfigObjectRepository objectRepo;

    public AppConfigTreeBuilder(AppConfigObjectRepository objectRepo) {
        this.objectRepo = objectRepo;
    }

    @Transactional(readOnly = true)
    public AppConfig buildTree() {
        List<AppConfigObjectEntity> all = objectRepo.findAll();
        if (all.isEmpty()) return null;

        Map<Long, List<AppConfigObjectEntity>> childrenByParentId = all.stream()
                .filter(o -> o.getParentObjectId() != null)
                .collect(Collectors.groupingBy(AppConfigObjectEntity::getParentObjectId));

        AppConfigObjectEntity root = all.stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElse(null);

        if (root == null) return null;

        return buildAppConfig(root, childrenByParentId);
    }

    private AppConfig buildAppConfig(AppConfigObjectEntity entity,
                                     Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        AppConfig config = new AppConfig();
        config.setId(entity.getId());
        config.setCode(entity.getCode());

        Map<String, DataForm> dataForms = new LinkedHashMap<>();
        Map<String, EntityProvider> entityProviders = new LinkedHashMap<>();
        Map<String, EntityRenderer> entityRenderers = new LinkedHashMap<>();

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String typeCode = child.getType().getCode();
            if ("DataForm".equals(typeCode)) {
                DataForm form = buildDataForm(child, childrenByParentId);
                dataForms.put(form.getCode(), form);
            } else if ("EntityProvider".equals(typeCode)) {
                EntityProvider provider = buildEntityProvider(child, childrenByParentId);
                entityProviders.put(provider.getCode(), provider);
            } else if ("EntityRenderer".equals(typeCode)) {
                EntityRenderer renderer = buildEntityRenderer(child, childrenByParentId);
                entityRenderers.put(renderer.getCode(), renderer);
            }
        }
        config.setDataForms(dataForms);
        config.setEntityProviders(entityProviders);
        config.setEntityRenderers(entityRenderers);
        return config;
    }

    private DataForm buildDataForm(AppConfigObjectEntity entity,
                                   Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        DataForm form = new DataForm();
        form.setId(entity.getId());
        form.setCode(entity.getCode());

        Map<String, DataFormElement> elements = new LinkedHashMap<>();
        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            if ("DataFormElement".equals(child.getType().getCode())) {
                DataFormElement element = buildDataFormElement(child, childrenByParentId);
                elements.put(element.getCode(), element);
            } else if ("DataFormEntityType".equals(child.getType().getCode()) && child.getEnumValue() != null) {
                form.setEntity(DataFormEntityType.valueOf(child.getEnumValue()));
                form.setEntityNodeId(child.getId());
            }
        }
        form.setElements(elements);
        return form;
    }

    private DataFormElement buildDataFormElement(AppConfigObjectEntity entity,
                                                 Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        DataFormElement element = new DataFormElement();
        element.setId(entity.getId());
        element.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("DataFormElementType".equals(childTypeCode) && child.getEnumValue() != null) {
                element.setType(DataFormElementType.valueOf(child.getEnumValue()));
                element.setTypeNodeId(child.getId());
            } else if ("DataBinding".equals(childTypeCode)) {
                element.setDataBinding(child.getCode());
                element.setDataBindingNodeId(child.getId());
            } else if ("EntityProviderRef".equals(childTypeCode)) {
                element.setEntityProviderRef(child.getCode());
                element.setEntityProviderRefNodeId(child.getId());
            } else if ("EntityRendererRef".equals(childTypeCode)) {
                element.setEntityRendererRef(child.getCode());
                element.setEntityRendererRefNodeId(child.getId());
            }
        }
        return element;
    }

    private EntityProvider buildEntityProvider(AppConfigObjectEntity entity,
                                               Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        EntityProvider provider = new EntityProvider();
        provider.setId(entity.getId());
        provider.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            if ("EntityProviderEntityType".equals(child.getType().getCode()) && child.getEnumValue() != null) {
                provider.setEntityType(DataFormEntityType.valueOf(child.getEnumValue()));
                provider.setEntityTypeNodeId(child.getId());
            }
        }
        return provider;
    }

    private EntityRenderer buildEntityRenderer(AppConfigObjectEntity entity,
                                                Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        EntityRenderer renderer = new EntityRenderer();
        renderer.setId(entity.getId());
        renderer.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("EntityRendererEntityType".equals(childTypeCode) && child.getEnumValue() != null) {
                renderer.setEntityType(DataFormEntityType.valueOf(child.getEnumValue()));
                renderer.setEntityTypeNodeId(child.getId());
            } else if ("EntityRendererTemplate".equals(childTypeCode)) {
                renderer.setTemplate(child.getCode());
                renderer.setTemplateNodeId(child.getId());
            }
        }
        return renderer;
    }

    private List<AppConfigObjectEntity> childrenOf(Long parentId,
                                                    Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        return childrenByParentId.getOrDefault(parentId, List.of());
    }
}
