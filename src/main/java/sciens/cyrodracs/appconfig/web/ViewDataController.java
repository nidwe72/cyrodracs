package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.service.ViewDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/view")
@CrossOrigin(origins = "*")
public class ViewDataController {

    private final ViewDataService viewDataService;

    public ViewDataController(ViewDataService viewDataService) {
        this.viewDataService = viewDataService;
    }

    @GetMapping("/{viewNodeCode}/data")
    public ResponseEntity<List<Map<String, Object>>> getData(@PathVariable String viewNodeCode) {
        try {
            List<Map<String, Object>> data = viewDataService.getData(viewNodeCode);
            return ResponseEntity.ok(data);
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
