package sciens.cyrodracs.appconfig;

public class Expression implements Coded {

    private Long id;
    private String code;
    private ExpressionType type;
    private Long typeNodeId;
    private String expression;
    private Long expressionNodeId;
    private InjectableBaseClass baseClass;
    private Long baseClassNodeId;
    private String description;
    private Long descriptionNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public ExpressionType getType() { return type; }
    public void setType(ExpressionType type) { this.type = type; }

    public Long getTypeNodeId() { return typeNodeId; }
    public void setTypeNodeId(Long typeNodeId) { this.typeNodeId = typeNodeId; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public Long getExpressionNodeId() { return expressionNodeId; }
    public void setExpressionNodeId(Long expressionNodeId) { this.expressionNodeId = expressionNodeId; }

    public InjectableBaseClass getBaseClass() { return baseClass; }
    public void setBaseClass(InjectableBaseClass baseClass) { this.baseClass = baseClass; }

    public Long getBaseClassNodeId() { return baseClassNodeId; }
    public void setBaseClassNodeId(Long baseClassNodeId) { this.baseClassNodeId = baseClassNodeId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getDescriptionNodeId() { return descriptionNodeId; }
    public void setDescriptionNodeId(Long descriptionNodeId) { this.descriptionNodeId = descriptionNodeId; }
}
