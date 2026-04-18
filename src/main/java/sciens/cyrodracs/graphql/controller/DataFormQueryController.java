package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.appconfig.service.DataFormEvaluationService;
import sciens.cyrodracs.appconfig.service.DataFormPersistenceService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DataFormQueryController {

    private final DataFormEvaluationService evaluationService;
    private final DataFormPersistenceService persistenceService;

    public DataFormQueryController(DataFormEvaluationService evaluationService,
                                    DataFormPersistenceService persistenceService) {
        this.evaluationService = evaluationService;
        this.persistenceService = persistenceService;
    }

    @QueryMapping
    public DataFormData dataFormData(@Argument String dataFormCode, @Argument Long entityId) {
        return persistenceService.load(dataFormCode, entityId);
    }

    @QueryMapping
    public Map<String, Object> evaluateDataForm(@Argument Map<String, Object> input) {
        String dataFormCode = (String) input.get("dataFormCode");
        Long entityId = input.get("entityId") != null ? ((Number) input.get("entityId")).longValue() : null;
        String changedElement = (String) input.get("changedElement");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> formStateEntries = (List<Map<String, String>>) input.getOrDefault("formState", List.of());
        Map<String, String> formState = formStateEntries.stream()
                .collect(Collectors.toMap(e -> e.get("key"), e -> e.get("value")));

        Map<String, ElementState> result = evaluationService.evaluate(
                dataFormCode, entityId, changedElement, formState);

        // Convert Map<String, ElementState> → List<ElementStateEntry>
        List<Map<String, Object>> elements = result.entrySet().stream()
                .map(e -> Map.<String, Object>of("key", e.getKey(), "value", e.getValue()))
                .toList();

        return Map.of("elements", elements);
    }
}
