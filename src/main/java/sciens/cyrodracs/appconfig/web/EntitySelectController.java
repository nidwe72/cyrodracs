package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.EntityOption;
import sciens.cyrodracs.appconfig.service.EntitySelectService;

import java.util.List;

@RestController
@RequestMapping("/api/entity-select")
@CrossOrigin(origins = "*")
public class EntitySelectController {

    private final EntitySelectService entitySelectService;

    public EntitySelectController(EntitySelectService entitySelectService) {
        this.entitySelectService = entitySelectService;
    }

    @GetMapping("/options")
    public ResponseEntity<List<EntityOption>> getOptions(
            @RequestParam String provider,
            @RequestParam String renderer) {
        try {
            List<EntityOption> options = entitySelectService.getOptions(provider, renderer);
            return ResponseEntity.ok(options);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
