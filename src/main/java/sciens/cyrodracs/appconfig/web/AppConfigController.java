package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;
import sciens.cyrodracs.appconfig.service.AppConfigMutationService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/app-config")
@CrossOrigin(origins = "*")
public class AppConfigController {

    private final AppConfigStore store;
    private final AppConfigMutationService mutationService;
    private final AppConfigTypeRepository typeRepo;

    public AppConfigController(AppConfigStore store,
                               AppConfigMutationService mutationService,
                               AppConfigTypeRepository typeRepo) {
        this.store = store;
        this.mutationService = mutationService;
        this.typeRepo = typeRepo;
    }

    /** Returns the full AppConfig tree from the application-scope store. */
    @GetMapping
    public ResponseEntity<AppConfig> getAppConfig() {
        AppConfig config = store.getAppConfig();
        if (config == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(config);
    }

    /** Returns all type definitions so the client knows the valid child types for each node. */
    @GetMapping("/types")
    public List<AppConfigTypeEntity> getTypes() {
        return typeRepo.findAll();
    }

    /** Adds a new node to the config tree. Returns the updated tree. */
    @PostMapping("/node")
    public ResponseEntity<AppConfig> addNode(@RequestBody AddNodeRequest request) {
        mutationService.addNode(request);
        store.reload();
        return ResponseEntity.ok(store.getAppConfig());
    }

    /** Deletes a node and all its descendants. Returns the updated tree. */
    @DeleteMapping("/node/{id}")
    public ResponseEntity<AppConfig> deleteNode(@PathVariable Long id) {
        mutationService.deleteNode(id);
        store.reload();
        return ResponseEntity.ok(store.getAppConfig());
    }

    /** Returns the enum constant names for a given type code (e.g. DataFormElementType, DataFormEntityType). */
    @GetMapping("/types/{typeCode}/enum-values")
    public ResponseEntity<List<String>> getEnumValues(@PathVariable String typeCode) {
        AppConfigTypeEntity type = typeRepo.findByCode(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + typeCode));
        if (!type.isEnumType() || type.getJavaType() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Class<?> enumClass = Class.forName(type.getJavaType());
            List<String> values = Arrays.stream(enumClass.getEnumConstants())
                    .map(c -> ((Enum<?>) c).name())
                    .toList();
            return ResponseEntity.ok(values);
        } catch (ClassNotFoundException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Updates the code and/or enumValue of a node. Returns the updated tree. */
    @PatchMapping("/node/{id}")
    public ResponseEntity<AppConfig> updateNode(@PathVariable Long id,
                                                @RequestBody UpdateNodeRequest request) {
        mutationService.updateNode(id, request);
        store.reload();
        return ResponseEntity.ok(store.getAppConfig());
    }
}
