package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.service.ViewDataService;

@RestController
@RequestMapping("/api/view")
@CrossOrigin(origins = "*")
public class ViewDataController {

    private final ViewDataService viewDataService;

    public ViewDataController(ViewDataService viewDataService) {
        this.viewDataService = viewDataService;
    }

    @GetMapping("/{viewNodeCode}/data")
    public ResponseEntity<PagedResponse> getData(
            @PathVariable String viewNodeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            var result = viewDataService.getDataPaged(viewNodeCode, page, size);
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

    @DeleteMapping("/{viewNodeCode}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String viewNodeCode, @PathVariable Long id) {
        try {
            viewDataService.delete(viewNodeCode, id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
