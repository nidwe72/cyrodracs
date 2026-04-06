package sciens.cyrodracs.appconfig;

public class VisibilityRule implements Coded {

    private Long id;
    private String code;
    private String expressionRef;
    private Long expressionRefNodeId;
    private VisibilityOperator operator;
    private Long operatorNodeId;
    private String compareValue;
    private Long compareValueNodeId;

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

    public VisibilityOperator getOperator() { return operator; }
    public void setOperator(VisibilityOperator operator) { this.operator = operator; }

    public Long getOperatorNodeId() { return operatorNodeId; }
    public void setOperatorNodeId(Long operatorNodeId) { this.operatorNodeId = operatorNodeId; }

    public String getCompareValue() { return compareValue; }
    public void setCompareValue(String compareValue) { this.compareValue = compareValue; }

    public Long getCompareValueNodeId() { return compareValueNodeId; }
    public void setCompareValueNodeId(Long compareValueNodeId) { this.compareValueNodeId = compareValueNodeId; }
}
