package sciens.cyrodracs.appconfig.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "APP_CONFIG_OBJECT")
public class AppConfigObjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private AppConfigTypeEntity type;

    @Column(name = "code", nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_object_id")
    private AppConfigObjectEntity parentObject;

    // Redundant FK column for read access without triggering lazy load
    @Column(name = "parent_object_id", insertable = false, updatable = false)
    private Long parentObjectId;

    @Column(name = "enum_value")
    private String enumValue;

    public Long getId() { return id; }

    public AppConfigTypeEntity getType() { return type; }
    public void setType(AppConfigTypeEntity type) { this.type = type; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public AppConfigObjectEntity getParentObject() { return parentObject; }
    public void setParentObject(AppConfigObjectEntity parentObject) { this.parentObject = parentObject; }

    public Long getParentObjectId() {
        if (parentObjectId != null) return parentObjectId;
        return parentObject != null ? parentObject.getId() : null;
    }

    public String getEnumValue() { return enumValue; }
    public void setEnumValue(String enumValue) { this.enumValue = enumValue; }
}
