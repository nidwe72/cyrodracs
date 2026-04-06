package sciens.cyrodracs.camera;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraLensMountRepository extends JpaRepository<CameraLensMount, Long> {
    Optional<CameraLensMount> findByName(String name);
}
