package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.DataFormData;
import sciens.cyrodracs.appconfig.service.DataFormPersistenceService;

@RestController
@RequestMapping("/api/data-form-data")
@CrossOrigin(origins = "*")
public class DataFormDataController {

    private final DataFormPersistenceService persistenceService;

    public DataFormDataController(DataFormPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @PostMapping
    public ResponseEntity<DataFormData> save(@RequestBody DataFormData data) {
        DataFormData result = persistenceService.save(data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{dataFormCode}/{entityId}")
    public ResponseEntity<DataFormData> load(@PathVariable String dataFormCode,
                                             @PathVariable Long entityId) {
        DataFormData result = persistenceService.load(dataFormCode, entityId);
        return ResponseEntity.ok(result);
    }
}
