package sciens.cyrodracs;

import java.util.List;

public class TestNode {

    private String code;
    private List<TestNode> children;

    public TestNode(String code, List<TestNode> children) {
        this.code = code;
        this.children = children;
    }

    public String getCode() {
        return code;
    }

    public List<TestNode> getChildren() {
        return children;
    }
}
