package sciens.cyrodracs.appconfig.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "APP_CONFIG_TYPE")
public class AppConfigTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_type_id")
    private AppConfigTypeEntity parentType;

    // Redundant FK column for read access without triggering lazy load
    @Column(name = "parent_type_id", insertable = false, updatable = false)
    private Long parentTypeId;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "is_collection", nullable = false)
    private boolean collection;

    @Column(name = "is_enum", nullable = false)
    private boolean enumType;

    @Column(name = "java_type")
    private String javaType;

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public AppConfigTypeEntity getParentType() { return parentType; }
    public void setParentType(AppConfigTypeEntity parentType) { this.parentType = parentType; }

    public Long getParentTypeId() {
        if (parentTypeId != null) return parentTypeId;
        return parentType != null ? parentType.getId() : null;
    }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public boolean isCollection() { return collection; }
    public void setCollection(boolean collection) { this.collection = collection; }

    public boolean isEnumType() { return enumType; }
    public void setEnumType(boolean enumType) { this.enumType = enumType; }

    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }
}
