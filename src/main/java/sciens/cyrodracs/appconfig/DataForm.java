package sciens.cyrodracs.appconfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataForm implements Coded {

    private Long id;
    private String code;
    private DataFormEntityType entity;
    private Long entityNodeId;
    private Map<String, DataFormElement> elements = new LinkedHashMap<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public DataFormEntityType getEntity() { return entity; }
    public void setEntity(DataFormEntityType entity) { this.entity = entity; }

    public Long getEntityNodeId() { return entityNodeId; }
    public void setEntityNodeId(Long entityNodeId) { this.entityNodeId = entityNodeId; }

    public Map<String, DataFormElement> getElements() { return elements; }
    public void setElements(Map<String, DataFormElement> elements) { this.elements = elements; }
}
