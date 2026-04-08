package sciens.cyrodracs.camera;

import jakarta.persistence.*;
import java.time.YearMonth;

@Entity
@Table(name = "CAMERA")
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "release_year")
    @Convert(converter = YearMonthConverter.class)
    private YearMonth releaseYear;

    @ManyToOne
    @JoinColumn(name = "producer_id")
    private CameraProducer producer;

    @ManyToOne
    @JoinColumn(name = "camera_lens_mount_2_camera_producer_id")
    private CameraLensMount2CameraProducer cameraLensMount2CameraProducer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public YearMonth getReleaseYear() { return releaseYear; }
    public void setReleaseYear(YearMonth releaseYear) { this.releaseYear = releaseYear; }

    public CameraProducer getProducer() { return producer; }
    public void setProducer(CameraProducer producer) { this.producer = producer; }

    public CameraLensMount2CameraProducer getCameraLensMount2CameraProducer() { return cameraLensMount2CameraProducer; }
    public void setCameraLensMount2CameraProducer(CameraLensMount2CameraProducer cameraLensMount2CameraProducer) { this.cameraLensMount2CameraProducer = cameraLensMount2CameraProducer; }
}
