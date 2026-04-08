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
    /** List of sibling element codes whose changes trigger a reload of this element. */
    private List<String> reloadOnChangeOf = new ArrayList<>();
    /** When true, the field must have a non-null value on save. Default: false. */
    private boolean mandatory;
    private Long mandatoryNodeId;
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

    public List<String> getReloadOnChangeOf() { return reloadOnChangeOf; }
    public void setReloadOnChangeOf(List<String> reloadOnChangeOf) { this.reloadOnChangeOf = reloadOnChangeOf; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public Long getMandatoryNodeId() { return mandatoryNodeId; }
    public void setMandatoryNodeId(Long mandatoryNodeId) { this.mandatoryNodeId = mandatoryNodeId; }

    public VisibilityRule getVisibilityRule() { return visibilityRule; }
    public void setVisibilityRule(VisibilityRule visibilityRule) { this.visibilityRule = visibilityRule; }
}
