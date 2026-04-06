package sciens.cyrodracs.camera;

import jakarta.persistence.*;

@Entity
@Table(name = "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"camera_lens_mount_id", "camera_producer_id"}))
public class CameraLensMount2CameraProducer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "camera_lens_mount_id")
    private CameraLensMount cameraLensMount;

    @ManyToOne
    @JoinColumn(name = "camera_producer_id")
    private CameraProducer cameraProducer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CameraLensMount getCameraLensMount() { return cameraLensMount; }
    public void setCameraLensMount(CameraLensMount cameraLensMount) { this.cameraLensMount = cameraLensMount; }

    public CameraProducer getCameraProducer() { return cameraProducer; }
    public void setCameraProducer(CameraProducer cameraProducer) { this.cameraProducer = cameraProducer; }
}
