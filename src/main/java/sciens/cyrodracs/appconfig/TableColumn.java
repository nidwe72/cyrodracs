package sciens.cyrodracs.appconfig;

public class TableColumn implements Coded {

    private Long id;
    private String code;
    private String key;
    private Long keyNodeId;
    private String header;
    private Long headerNodeId;
    private String entityRendererRef;
    private Long entityRendererRefNodeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Long getKeyNodeId() { return keyNodeId; }
    public void setKeyNodeId(Long keyNodeId) { this.keyNodeId = keyNodeId; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }

    public Long getHeaderNodeId() { return headerNodeId; }
    public void setHeaderNodeId(Long headerNodeId) { this.headerNodeId = headerNodeId; }

    public String getEntityRendererRef() { return entityRendererRef; }
    public void setEntityRendererRef(String entityRendererRef) { this.entityRendererRef = entityRendererRef; }

    public Long getEntityRendererRefNodeId() { return entityRendererRefNodeId; }
    public void setEntityRendererRefNodeId(Long entityRendererRefNodeId) { this.entityRendererRefNodeId = entityRendererRefNodeId; }
}
