package sciens.cyrodracs.appconfig.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.BindingProposalResponse;
import sciens.cyrodracs.appconfig.DataFormEntityType;
import sciens.cyrodracs.appconfig.service.DataBindingService;

@RestController
@RequestMapping("/api/data-binding")
@CrossOrigin(origins = "*")
public class DataBindingController {

    private final DataBindingService dataBindingService;

    public DataBindingController(DataBindingService dataBindingService) {
        this.dataBindingService = dataBindingService;
    }

    @GetMapping("/proposals/{entityType}")
    public ResponseEntity<BindingProposalResponse> getProposals(
            @PathVariable String entityType,
            @RequestParam(required = false, defaultValue = "") String prefix) {
        try {
            DataFormEntityType type = DataFormEntityType.valueOf(entityType);
            BindingProposalResponse response = dataBindingService.getProposals(type, prefix);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
