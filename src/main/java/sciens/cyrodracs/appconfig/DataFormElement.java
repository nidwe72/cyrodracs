package sciens.cyrodracs.appconfig;

public class DataFormElement implements Coded {

    private Long id;
    private String code;
    private DataFormElementType type;
    /** DB id of the DataFormElementType child object; null when no type has been set yet. */
    private Long typeNodeId;
    /** The entity attribute path this element binds to (e.g. "name", "producer.name"). */
    private String dataBinding;
    /** DB id of the DataBinding child object in the config tree. */
    private Long dataBindingNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public DataFormElementType getType() { return type; }
    public void setType(DataFormElementType type) { this.type = type; }

    public Long getTypeNodeId() { return typeNodeId; }
    public void setTypeNodeId(Long typeNodeId) { this.typeNodeId = typeNodeId; }

    public String getDataBinding() { return dataBinding; }
    public void setDataBinding(String dataBinding) { this.dataBinding = dataBinding; }

    public Long getDataBindingNodeId() { return dataBindingNodeId; }
    public void setDataBindingNodeId(Long dataBindingNodeId) { this.dataBindingNodeId = dataBindingNodeId; }
}
