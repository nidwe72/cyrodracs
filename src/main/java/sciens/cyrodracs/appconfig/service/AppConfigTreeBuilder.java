package sciens.cyrodracs.appconfig.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
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
        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            if ("DataForm".equals(child.getType().getCode())) {
                DataForm form = buildDataForm(child, childrenByParentId);
                dataForms.put(form.getCode(), form);
            }
        }
        config.setDataForms(dataForms);
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
            if ("DataFormElementType".equals(child.getType().getCode()) && child.getEnumValue() != null) {
                element.setType(DataFormElementType.valueOf(child.getEnumValue()));
                element.setTypeNodeId(child.getId());
            }
        }
        return element;
    }

    private List<AppConfigObjectEntity> childrenOf(Long parentId,
                                                    Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        return childrenByParentId.getOrDefault(parentId, List.of());
    }
}
