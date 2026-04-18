package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.camera.*;

import java.util.List;

@Controller
public class CameraQueryController {

    private final CameraRepository cameraRepo;
    private final CameraProducerRepository producerRepo;
    private final CameraLensMountRepository lensMountRepo;

    public CameraQueryController(CameraRepository cameraRepo,
                                  CameraProducerRepository producerRepo,
                                  CameraLensMountRepository lensMountRepo) {
        this.cameraRepo = cameraRepo;
        this.producerRepo = producerRepo;
        this.lensMountRepo = lensMountRepo;
    }

    @QueryMapping
    public List<Camera> cameras() { return cameraRepo.findAll(); }

    @QueryMapping
    public List<CameraProducer> cameraProducers() { return producerRepo.findAll(); }

    @QueryMapping
    public List<CameraLensMount> cameraLensMounts() { return lensMountRepo.findAll(); }
}
