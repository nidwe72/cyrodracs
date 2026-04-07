package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.service.GridDataService;

import java.util.Map;

@RestController
@RequestMapping("/api/view/grid-data")
@CrossOrigin(origins = "*")
public class GridDataController {

    private final GridDataService gridDataService;

    public GridDataController(GridDataService gridDataService) {
        this.gridDataService = gridDataService;
    }

    @PostMapping("/{dataFormCode}/{elementCode}")
    public ResponseEntity<PagedResponse> getGridData(
            @PathVariable String dataFormCode,
            @PathVariable String elementCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestBody GridDataRequest request) {
        try {
            var result = gridDataService.getGridData(
                    dataFormCode, elementCode,
                    request.entityId(), request.formState(),
                    page, size);
            return ResponseEntity.ok(new PagedResponse(
                    result.items(),
                    result.totalCount(),
                    result.page(),
                    result.pageSize(),
                    result.totalPages()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{dataFormCode}/{elementCode}/{entityId}")
    public ResponseEntity<Void> deleteGridEntity(
            @PathVariable String dataFormCode,
            @PathVariable String elementCode,
            @PathVariable Long entityId) {
        try {
            gridDataService.deleteGridEntity(dataFormCode, elementCode, entityId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record GridDataRequest(Long entityId, Map<String, String> formState) {}
}
