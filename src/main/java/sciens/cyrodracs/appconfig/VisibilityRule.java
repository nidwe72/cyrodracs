package sciens.cyrodracs.appconfig;

public class VisibilityRule implements Coded {

    private Long id;
    private String code;
    private String expressionRef;
    private Long expressionRefNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public String getExpressionRef() { return expressionRef; }
    public void setExpressionRef(String expressionRef) { this.expressionRef = expressionRef; }

    public Long getExpressionRefNodeId() { return expressionRefNodeId; }
    public void setExpressionRefNodeId(Long expressionRefNodeId) { this.expressionRefNodeId = expressionRefNodeId; }
}
