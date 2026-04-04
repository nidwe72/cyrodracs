package sciens.cyrodracs.appconfig;

public class BindingCompletion {

    private String segment;
    private String javaType;
    private boolean leaf;
    private String suggestedElementType;
    private String referencedEntityType;

    public BindingCompletion() {}

    public BindingCompletion(String segment, String javaType, boolean leaf,
                             String suggestedElementType, String referencedEntityType) {
        this.segment = segment;
        this.javaType = javaType;
        this.leaf = leaf;
        this.suggestedElementType = suggestedElementType;
        this.referencedEntityType = referencedEntityType;
    }

    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }

    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }

    public boolean isLeaf() { return leaf; }
    public void setLeaf(boolean leaf) { this.leaf = leaf; }

    public String getSuggestedElementType() { return suggestedElementType; }
    public void setSuggestedElementType(String suggestedElementType) { this.suggestedElementType = suggestedElementType; }

    public String getReferencedEntityType() { return referencedEntityType; }
    public void setReferencedEntityType(String referencedEntityType) { this.referencedEntityType = referencedEntityType; }
}
