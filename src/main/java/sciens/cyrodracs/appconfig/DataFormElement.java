package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class DataFormElement implements Coded {

    private Long id;
    private String code;
    private DataFormElementType type;
    private Long typeNodeId;
    private String dataBinding;
    private Long dataBindingNodeId;
    private String entityProviderRef;
    private Long entityProviderRefNodeId;
    private String entityRendererRef;
    private Long entityRendererRefNodeId;
    /** GRID: column definitions for the embedded table. */
    private List<TableColumn> tableColumns = new ArrayList<>();
    /** GRID: add/edit action configuration with context bindings. */
    private AddAction addAction;
    /** When true, changing this element triggers reload of dependent elements (e.g., GRIDs). */
    private boolean reloadOnChange;
    private Long reloadOnChangeNodeId;
    /** Optional: controls whether this element is visible. Null = always visible. */
    private VisibilityRule visibilityRule;

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

    public String getEntityProviderRef() { return entityProviderRef; }
    public void setEntityProviderRef(String entityProviderRef) { this.entityProviderRef = entityProviderRef; }

    public Long getEntityProviderRefNodeId() { return entityProviderRefNodeId; }
    public void setEntityProviderRefNodeId(Long entityProviderRefNodeId) { this.entityProviderRefNodeId = entityProviderRefNodeId; }

    public String getEntityRendererRef() { return entityRendererRef; }
    public void setEntityRendererRef(String entityRendererRef) { this.entityRendererRef = entityRendererRef; }

    public Long getEntityRendererRefNodeId() { return entityRendererRefNodeId; }
    public void setEntityRendererRefNodeId(Long entityRendererRefNodeId) { this.entityRendererRefNodeId = entityRendererRefNodeId; }

    public List<TableColumn> getTableColumns() { return tableColumns; }
    public void setTableColumns(List<TableColumn> tableColumns) { this.tableColumns = tableColumns; }

    public AddAction getAddAction() { return addAction; }
    public void setAddAction(AddAction addAction) { this.addAction = addAction; }

    public boolean isReloadOnChange() { return reloadOnChange; }
    public void setReloadOnChange(boolean reloadOnChange) { this.reloadOnChange = reloadOnChange; }

    public Long getReloadOnChangeNodeId() { return reloadOnChangeNodeId; }
    public void setReloadOnChangeNodeId(Long reloadOnChangeNodeId) { this.reloadOnChangeNodeId = reloadOnChangeNodeId; }

    public VisibilityRule getVisibilityRule() { return visibilityRule; }
    public void setVisibilityRule(VisibilityRule visibilityRule) { this.visibilityRule = visibilityRule; }
}
