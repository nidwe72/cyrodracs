package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.service.GridDataService;
import sciens.cyrodracs.appconfig.service.ViewDataService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ViewQueryController {

    private final ViewDataService viewDataService;
    private final GridDataService gridDataService;

    public ViewQueryController(ViewDataService viewDataService, GridDataService gridDataService) {
        this.viewDataService = viewDataService;
        this.gridDataService = gridDataService;
    }

    @QueryMapping
    public Map<String, Object> viewData(@Argument String viewNodeCode,
                                         @Argument Integer page,
                                         @Argument Integer size) {
        int p = page != null ? page : 0;
        int s = size != null ? size : 10;
        var result = viewDataService.getDataPaged(viewNodeCode, p, s);
        return Map.of(
                "items", result.items(),
                "totalCount", result.totalCount(),
                "page", result.page(),
                "pageSize", s,
                "totalPages", result.totalPages()
        );
    }

    @QueryMapping
    public Map<String, Object> gridData(@Argument Map<String, Object> input) {
        String dataFormCode = (String) input.get("dataFormCode");
        String elementCode = (String) input.get("elementCode");
        Long entityId = ((Number) input.get("entityId")).longValue();
        int page = input.get("page") != null ? ((Number) input.get("page")).intValue() : 0;
        int size = input.get("size") != null ? ((Number) input.get("size")).intValue() : 10;

        @SuppressWarnings("unchecked")
        List<Map<String, String>> formStateEntries = (List<Map<String, String>>) input.getOrDefault("formState", List.of());
        Map<String, String> formState = formStateEntries.stream()
                .collect(Collectors.toMap(e -> e.get("key"), e -> e.get("value")));

        var result = gridDataService.getGridData(dataFormCode, elementCode, entityId, formState, page, size);
        return Map.of(
                "items", result.items(),
                "totalCount", result.totalCount(),
                "page", result.page(),
                "pageSize", size,
                "totalPages", result.totalPages()
        );
    }
}
