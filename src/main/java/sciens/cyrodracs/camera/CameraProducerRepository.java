package sciens.cyrodracs.camera;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraProducerRepository extends JpaRepository<CameraProducer, Long> {
    Optional<CameraProducer> findByName(String name);
}
