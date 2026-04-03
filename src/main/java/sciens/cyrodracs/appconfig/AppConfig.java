package sciens.cyrodracs.appconfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppConfig implements Coded {

    private Long id;
    private String code;
    private Map<String, DataForm> dataForms = new LinkedHashMap<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public Map<String, DataForm> getDataForms() { return dataForms; }
    public void setDataForms(Map<String, DataForm> dataForms) { this.dataForms = dataForms; }
}
