package sciens.cyrodracs.camera;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraLensMount2CameraProducerRepository extends JpaRepository<CameraLensMount2CameraProducer, Long> {
    Optional<CameraLensMount2CameraProducer> findByCameraLensMountAndCameraProducer(
            CameraLensMount cameraLensMount, CameraProducer cameraProducer);
}
