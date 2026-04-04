package sciens.cyrodracs.appconfig;

public class SortField implements Coded {

    private Long id;
    private String code;
    private String field;
    private Long fieldNodeId;
    private SortDirection direction;
    private Long directionNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public Long getFieldNodeId() { return fieldNodeId; }
    public void setFieldNodeId(Long fieldNodeId) { this.fieldNodeId = fieldNodeId; }

    public SortDirection getDirection() { return direction; }
    public void setDirection(SortDirection direction) { this.direction = direction; }

    public Long getDirectionNodeId() { return directionNodeId; }
    public void setDirectionNodeId(Long directionNodeId) { this.directionNodeId = directionNodeId; }
}
