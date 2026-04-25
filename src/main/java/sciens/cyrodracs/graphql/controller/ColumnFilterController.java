package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.ColumnFilterMeta;
import sciens.cyrodracs.appconfig.PickerCandidatesPagedResult;
import sciens.cyrodracs.appconfig.service.ColumnFilterMetadataService;
import sciens.cyrodracs.appconfig.service.PickerCandidatesService;

import java.util.List;
import java.util.Map;

@Controller
public class ColumnFilterController {

    private final ColumnFilterMetadataService metadataService;
    private final PickerCandidatesService pickerService;

    public ColumnFilterController(ColumnFilterMetadataService metadataService,
                                  PickerCandidatesService pickerService) {
        this.metadataService = metadataService;
        this.pickerService = pickerService;
    }

    @QueryMapping
    public List<ColumnFilterMeta> columnFilterMetadata(@Argument Map<String, Object> scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        String viewNodeCode = (String) scope.get("viewNodeCode");
        String dataFormCode = (String) scope.get("dataFormCode");
        String elementCode = (String) scope.get("elementCode");
        return metadataService.getMetadata(viewNodeCode, dataFormCode, elementCode);
    }

    @QueryMapping
    @SuppressWarnings("unchecked")
    public PickerCandidatesPagedResult entityRefPickerCandidates(@Argument Map<String, Object> input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        Map<String, Object> scope = (Map<String, Object>) input.get("scope");
        if (scope == null) {
            throw new IllegalArgumentException("input.scope is required");
        }
        String viewNodeCode = (String) scope.get("viewNodeCode");
        String dataFormCode = (String) scope.get("dataFormCode");
        String elementCode = (String) scope.get("elementCode");
        String columnKey = (String) input.get("columnKey");
        String term = (String) input.get("term");
        Integer page = (Integer) input.get("page");
        Integer size = (Integer) input.get("size");
        return pickerService.getCandidates(
                viewNodeCode, dataFormCode, elementCode,
                columnKey, term,
                page != null ? page : 0,
                size != null ? size : 20);
    }
}
