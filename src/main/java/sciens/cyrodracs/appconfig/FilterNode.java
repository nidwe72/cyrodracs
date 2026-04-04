package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class FilterNode implements Coded {

    private Long id;
    private String code;
    private FilterNodeType type;
    private Long typeNodeId;
    private String field;
    private Long fieldNodeId;
    private FilterOperator operator;
    private Long operatorNodeId;
    private String value;
    private Long valueNodeId;
    private List<String> values = new ArrayList<>();
    private List<FilterNode> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public FilterNodeType getType() { return type; }
    public void setType(FilterNodeType type) { this.type = type; }

    public Long getTypeNodeId() { return typeNodeId; }
    public void setTypeNodeId(Long typeNodeId) { this.typeNodeId = typeNodeId; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public Long getFieldNodeId() { return fieldNodeId; }
    public void setFieldNodeId(Long fieldNodeId) { this.fieldNodeId = fieldNodeId; }

    public FilterOperator getOperator() { return operator; }
    public void setOperator(FilterOperator operator) { this.operator = operator; }

    public Long getOperatorNodeId() { return operatorNodeId; }
    public void setOperatorNodeId(Long operatorNodeId) { this.operatorNodeId = operatorNodeId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Long getValueNodeId() { return valueNodeId; }
    public void setValueNodeId(Long valueNodeId) { this.valueNodeId = valueNodeId; }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }

    public List<FilterNode> getChildren() { return children; }
    public void setChildren(List<FilterNode> children) { this.children = children; }
}
