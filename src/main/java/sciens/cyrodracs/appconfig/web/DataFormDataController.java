package sciens.cyrodracs.appconfig.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.DataFormData;
import sciens.cyrodracs.appconfig.service.DataFormPersistenceService;

import java.util.Map;

@RestController
@RequestMapping("/api/data-form-data")
@CrossOrigin(origins = "*")
public class DataFormDataController {

    private final DataFormPersistenceService persistenceService;

    public DataFormDataController(DataFormPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody DataFormData data) {
        try {
            DataFormData result;
            if (data.getPendingChildren() != null && !data.getPendingChildren().isEmpty()) {
                result = persistenceService.saveWithChildren(data);
            } else {
                result = persistenceService.save(data);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            // Application-level validation (e.g., unique constraint check, entity not found)
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "dataFormCode", data.getDataFormCode()
            ));
        } catch (DataIntegrityViolationException e) {
            // DB-level constraint violation (fallback if application check didn't catch it)
            String message = extractConstraintMessage(e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", message,
                    "dataFormCode", data.getDataFormCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Save failed: " + sanitizeMessage(e.getMessage()),
                    "dataFormCode", data.getDataFormCode()
            ));
        }
    }

    private String extractConstraintMessage(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg == null) return "Data integrity violation";

        // SQLite unique constraint: "UNIQUE constraint failed: table.col1, table.col2"
        if (msg.contains("UNIQUE constraint failed")) {
            String fields = msg.substring(msg.indexOf(':') + 1).trim();
            // Strip table prefix from each field: "table.col1, table.col2" → "col1, col2"
            String cleaned = fields.replaceAll("[a-zA-Z_]+\\.", "");
            return "Duplicate entry: conflicting values on " + cleaned;
        }

        // SQLite NOT NULL: "NOT NULL constraint failed: table.column"
        if (msg.contains("NOT NULL constraint failed")) {
            String field = msg.substring(msg.lastIndexOf('.') + 1).trim();
            return field + " is required";
        }

        return "Data integrity violation: " + sanitizeMessage(msg);
    }

    private String sanitizeMessage(String msg) {
        if (msg == null) return "unknown error";
        // Truncate long messages and strip internal details
        if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
        return msg;
    }

    @GetMapping("/{dataFormCode}/{entityId}")
    public ResponseEntity<DataFormData> load(@PathVariable String dataFormCode,
                                             @PathVariable Long entityId) {
        DataFormData result = persistenceService.load(dataFormCode, entityId);
        return ResponseEntity.ok(result);
    }
}
