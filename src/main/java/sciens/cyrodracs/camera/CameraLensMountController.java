package sciens.cyrodracs.camera;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/camera-lens-mounts")
@CrossOrigin(origins = "*")
public class CameraLensMountController {

    private final CameraLensMountRepository repository;

    public CameraLensMountController(CameraLensMountRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CameraLensMount> getAll() {
        return repository.findAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
