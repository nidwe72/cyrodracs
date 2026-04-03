package sciens.cyrodracs.appconfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataFormData {

    private String dataFormCode;
    private Long entityId;
    private Map<String, Object> values = new LinkedHashMap<>();

    public String getDataFormCode() { return dataFormCode; }
    public void setDataFormCode(String dataFormCode) { this.dataFormCode = dataFormCode; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }
}
