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
    /** CF3.4.5 — when true (default), the column's filter input restricts
     *  its options to values present in visible rows. False opts out
     *  (ENUM dropdown shows all declared constants; ENTITY_REF picker
     *  shows all entities of the target type). No effect for STRING /
     *  NUMBER / DATE / BOOLEAN columns (no restriction concept exists). */
    private boolean restrictByVisibleRows = true;
    private Long restrictByVisibleRowsNodeId;

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

    public boolean isRestrictByVisibleRows() { return restrictByVisibleRows; }
    public void setRestrictByVisibleRows(boolean restrictByVisibleRows) { this.restrictByVisibleRows = restrictByVisibleRows; }

    public Long getRestrictByVisibleRowsNodeId() { return restrictByVisibleRowsNodeId; }
    public void setRestrictByVisibleRowsNodeId(Long restrictByVisibleRowsNodeId) { this.restrictByVisibleRowsNodeId = restrictByVisibleRowsNodeId; }
}
