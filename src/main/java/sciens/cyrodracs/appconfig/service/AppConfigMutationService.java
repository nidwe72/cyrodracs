package sciens.cyrodracs.appconfig.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;
import sciens.cyrodracs.appconfig.web.AddNodeRequest;
import sciens.cyrodracs.appconfig.web.UpdateNodeRequest;

import java.util.List;

@Service
public class AppConfigMutationService {

    private final AppConfigObjectRepository objectRepo;
    private final AppConfigTypeRepository typeRepo;

    public AppConfigMutationService(AppConfigObjectRepository objectRepo,
                                    AppConfigTypeRepository typeRepo) {
        this.objectRepo = objectRepo;
        this.typeRepo = typeRepo;
    }

    @Transactional
    public void addNode(AddNodeRequest request) {
        AppConfigTypeEntity type = typeRepo.findByCode(request.typeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown type code: " + request.typeCode()));

        AppConfigObjectEntity entity = new AppConfigObjectEntity();
        entity.setType(type);
        entity.setCode(request.code());
        entity.setEnumValue(request.enumValue());

        if (request.parentObjectId() != null) {
            AppConfigObjectEntity parent = objectRepo.findById(request.parentObjectId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent object not found: " + request.parentObjectId()));
            entity.setParentObject(parent);
        }

        objectRepo.save(entity);
    }

    @Transactional
    public void deleteNode(Long id) {
        AppConfigObjectEntity entity = objectRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));
        deleteRecursive(entity);
    }

    @Transactional
    public void updateNode(Long id, UpdateNodeRequest request) {
        AppConfigObjectEntity entity = objectRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));
        if (request.code() != null) entity.setCode(request.code());
        if (request.enumValue() != null) entity.setEnumValue(request.enumValue());
        objectRepo.save(entity);
    }

    @Transactional
    public Long copyNode(Long sourceId, String newCode) {
        AppConfigObjectEntity source = objectRepo.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + sourceId));
        AppConfigObjectEntity copy = copyRecursive(source, source.getParentObject(), newCode);
        return copy.getId();
    }

    private AppConfigObjectEntity copyRecursive(AppConfigObjectEntity source,
                                                 AppConfigObjectEntity newParent,
                                                 String overrideCode) {
        AppConfigObjectEntity copy = new AppConfigObjectEntity();
        copy.setType(source.getType());
        copy.setCode(overrideCode != null ? overrideCode : source.getCode());
        copy.setEnumValue(source.getEnumValue());
        copy.setParentObject(newParent);
        copy = objectRepo.save(copy);

        List<AppConfigObjectEntity> children = objectRepo.findByParentObject(source);
        for (AppConfigObjectEntity child : children) {
            copyRecursive(child, copy, null);
        }
        return copy;
    }

    private void deleteRecursive(AppConfigObjectEntity entity) {
        List<AppConfigObjectEntity> children = objectRepo.findByParentObject(entity);
        for (AppConfigObjectEntity child : children) {
            deleteRecursive(child);
        }
        objectRepo.delete(entity);
    }
}
