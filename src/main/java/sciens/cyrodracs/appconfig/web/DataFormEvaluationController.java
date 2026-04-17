package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.ElementState;
import sciens.cyrodracs.appconfig.service.DataFormEvaluationService;

import java.util.Map;

@RestController
@RequestMapping("/api/data-form")
@CrossOrigin(origins = "*")
public class DataFormEvaluationController {

    private final DataFormEvaluationService evaluationService;

    public DataFormEvaluationController(DataFormEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateResponse> evaluate(@RequestBody EvaluateRequest request) {
        try {
            Map<String, ElementState> elements = evaluationService.evaluate(
                    request.dataFormCode(), request.entityId(),
                    request.changedElement(), request.formState());
            return ResponseEntity.ok(new EvaluateResponse(elements));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record EvaluateRequest(
            String dataFormCode,
            Long entityId,
            String changedElement,
            Map<String, String> formState) {}

    public record EvaluateResponse(Map<String, ElementState> elements) {}
}
