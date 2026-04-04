package sciens.cyrodracs.appconfig;

public class EntityProvider implements Coded {

    private Long id;
    private String code;
    private DataFormEntityType entityType;
    private Long entityTypeNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public DataFormEntityType getEntityType() { return entityType; }
    public void setEntityType(DataFormEntityType entityType) { this.entityType = entityType; }

    public Long getEntityTypeNodeId() { return entityTypeNodeId; }
    public void setEntityTypeNodeId(Long entityTypeNodeId) { this.entityTypeNodeId = entityTypeNodeId; }
}
