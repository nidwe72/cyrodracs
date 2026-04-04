package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class ViewNode implements Coded {

    private Long id;
    private String code;
    private ViewNodeType type;
    private Long typeNodeId;
    private String label;
    private Long labelNodeId;
    private String entityProviderRef;
    private Long entityProviderRefNodeId;
    private String dataFormRef;
    private Long dataFormRefNodeId;
    private String content;
    private Long contentNodeId;
    private List<ViewNode> children = new ArrayList<>();
    private List<TableColumn> tableColumns = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public ViewNodeType getType() { return type; }
    public void setType(ViewNodeType type) { this.type = type; }

    public Long getTypeNodeId() { return typeNodeId; }
    public void setTypeNodeId(Long typeNodeId) { this.typeNodeId = typeNodeId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Long getLabelNodeId() { return labelNodeId; }
    public void setLabelNodeId(Long labelNodeId) { this.labelNodeId = labelNodeId; }

    public String getEntityProviderRef() { return entityProviderRef; }
    public void setEntityProviderRef(String entityProviderRef) { this.entityProviderRef = entityProviderRef; }

    public Long getEntityProviderRefNodeId() { return entityProviderRefNodeId; }
    public void setEntityProviderRefNodeId(Long entityProviderRefNodeId) { this.entityProviderRefNodeId = entityProviderRefNodeId; }

    public String getDataFormRef() { return dataFormRef; }
    public void setDataFormRef(String dataFormRef) { this.dataFormRef = dataFormRef; }

    public Long getDataFormRefNodeId() { return dataFormRefNodeId; }
    public void setDataFormRefNodeId(Long dataFormRefNodeId) { this.dataFormRefNodeId = dataFormRefNodeId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getContentNodeId() { return contentNodeId; }
    public void setContentNodeId(Long contentNodeId) { this.contentNodeId = contentNodeId; }

    public List<ViewNode> getChildren() { return children; }
    public void setChildren(List<ViewNode> children) { this.children = children; }

    public List<TableColumn> getTableColumns() { return tableColumns; }
    public void setTableColumns(List<TableColumn> tableColumns) { this.tableColumns = tableColumns; }
}
