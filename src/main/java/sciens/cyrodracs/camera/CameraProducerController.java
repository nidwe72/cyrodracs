package sciens.cyrodracs.camera;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/camera-producers")
@CrossOrigin(origins = "*")
public class CameraProducerController {

    private final CameraProducerRepository repository;

    public CameraProducerController(CameraProducerRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CameraProducer> getAll() {
        return repository.findAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
