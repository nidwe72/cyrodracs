package sciens.cyrodracs.appconfig;

public class ContextBinding implements Coded {

    private Long id;
    private String code;
    private String target;
    private Long targetNodeId;
    private String source;
    private Long sourceNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public Long getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(Long targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(Long sourceNodeId) { this.sourceNodeId = sourceNodeId; }
}
