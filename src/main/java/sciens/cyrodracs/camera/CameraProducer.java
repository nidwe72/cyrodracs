package sciens.cyrodracs.camera;

import jakarta.persistence.*;
import java.time.YearMonth;

@Entity
@Table(name = "CAMERA_PRODUCER")
public class CameraProducer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "foundation_year")
    @Convert(converter = YearMonthConverter.class)
    private YearMonth foundationYear;

    @Column(name = "shutdown_year")
    @Convert(converter = YearMonthConverter.class)
    private YearMonth shutdownYear;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public YearMonth getFoundationYear() { return foundationYear; }
    public void setFoundationYear(YearMonth foundationYear) { this.foundationYear = foundationYear; }

    public YearMonth getShutdownYear() { return shutdownYear; }
    public void setShutdownYear(YearMonth shutdownYear) { this.shutdownYear = shutdownYear; }
}
