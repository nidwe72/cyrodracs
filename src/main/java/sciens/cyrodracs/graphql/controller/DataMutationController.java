package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.DataFormData;
import sciens.cyrodracs.appconfig.PendingChild;
import sciens.cyrodracs.appconfig.service.DataFormPersistenceService;
import sciens.cyrodracs.appconfig.service.GridDataService;
import sciens.cyrodracs.appconfig.service.ViewDataService;
import sciens.cyrodracs.camera.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DataMutationController {

    private final DataFormPersistenceService persistenceService;
    private final ViewDataService viewDataService;
    private final GridDataService gridDataService;
    private final CameraRepository cameraRepo;
    private final CameraProducerRepository producerRepo;
    private final CameraLensMountRepository lensMountRepo;

    public DataMutationController(DataFormPersistenceService persistenceService,
                                   ViewDataService viewDataService,
                                   GridDataService gridDataService,
                                   CameraRepository cameraRepo,
                                   CameraProducerRepository producerRepo,
                                   CameraLensMountRepository lensMountRepo) {
        this.persistenceService = persistenceService;
        this.viewDataService = viewDataService;
        this.gridDataService = gridDataService;
        this.cameraRepo = cameraRepo;
        this.producerRepo = producerRepo;
        this.lensMountRepo = lensMountRepo;
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveDataFormData(@Argument Map<String, Object> input) {
        try {
            DataFormData data = new DataFormData();
            data.setDataFormCode((String) input.get("dataFormCode"));
            if (input.get("entityId") != null) data.setEntityId(((Number) input.get("entityId")).longValue());
            if (input.get("values") != null) data.setValues(new LinkedHashMap<>((Map<String, Object>) input.get("values")));

            var pendingRaw = (List<Map<String, Object>>) input.get("pendingChildren");
            if (pendingRaw != null) {
                data.setPendingChildren(pendingRaw.stream().map(this::toPendingChild).toList());
            }

            DataFormData saved = data.getPendingChildren() != null && !data.getPendingChildren().isEmpty()
                    ? persistenceService.saveWithChildren(data)
                    : persistenceService.save(data);

            return Map.of("success", true, "data", saved);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "dataFormCode", input.getOrDefault("dataFormCode", ""));
        }
    }

    @SuppressWarnings("unchecked")
    private PendingChild toPendingChild(Map<String, Object> raw) {
        PendingChild child = new PendingChild();
        child.setDataFormCode((String) raw.get("dataFormCode"));
        child.setContextBindingTarget((String) raw.get("contextBindingTarget"));
        if (raw.get("values") != null) child.setValues(new LinkedHashMap<>((Map<String, Object>) raw.get("values")));
        var nested = (List<Map<String, Object>>) raw.get("pendingChildren");
        if (nested != null) child.setPendingChildren(nested.stream().map(this::toPendingChild).toList());
        return child;
    }

    @MutationMapping
    public boolean deleteCamera(@Argument Long id) { cameraRepo.deleteById(id); return true; }

    @MutationMapping
    public boolean deleteCameraProducer(@Argument Long id) { producerRepo.deleteById(id); return true; }

    @MutationMapping
    public boolean deleteCameraLensMount(@Argument Long id) { lensMountRepo.deleteById(id); return true; }

    @MutationMapping
    public boolean deleteViewEntity(@Argument String viewNodeCode, @Argument Long id) {
        viewDataService.delete(viewNodeCode, id);
        return true;
    }

    @MutationMapping
    public boolean deleteGridEntity(@Argument String dataFormCode, @Argument String elementCode, @Argument Long entityId) {
        gridDataService.deleteGridEntity(dataFormCode, elementCode, entityId);
        return true;
    }
}
