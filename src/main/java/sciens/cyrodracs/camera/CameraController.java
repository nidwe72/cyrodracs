package sciens.cyrodracs.camera;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@CrossOrigin(origins = "*")
public class CameraController {

    private final CameraRepository repository;

    public CameraController(CameraRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Camera> getAll() {
        return repository.findAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
