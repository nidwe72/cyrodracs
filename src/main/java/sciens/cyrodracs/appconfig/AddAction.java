package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class AddAction implements Coded {

    private Long id;
    private String code;
    private String targetDataFormRef;
    private Long targetDataFormRefNodeId;
    private String childLabel;
    private Long childLabelNodeId;
    private List<ContextBinding> contextBindings = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public String getTargetDataFormRef() { return targetDataFormRef; }
    public void setTargetDataFormRef(String targetDataFormRef) { this.targetDataFormRef = targetDataFormRef; }

    public Long getTargetDataFormRefNodeId() { return targetDataFormRefNodeId; }
    public void setTargetDataFormRefNodeId(Long targetDataFormRefNodeId) { this.targetDataFormRefNodeId = targetDataFormRefNodeId; }

    public String getChildLabel() { return childLabel; }
    public void setChildLabel(String childLabel) { this.childLabel = childLabel; }

    public Long getChildLabelNodeId() { return childLabelNodeId; }
    public void setChildLabelNodeId(Long childLabelNodeId) { this.childLabelNodeId = childLabelNodeId; }

    public List<ContextBinding> getContextBindings() { return contextBindings; }
    public void setContextBindings(List<ContextBinding> contextBindings) { this.contextBindings = contextBindings; }
}
