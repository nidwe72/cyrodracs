package sciens.cyrodracs.appconfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataForm implements Coded {

    private Long id;
    private String code;
    private Map<String, DataFormElement> elements = new LinkedHashMap<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public Map<String, DataFormElement> getElements() { return elements; }
    public void setElements(Map<String, DataFormElement> elements) { this.elements = elements; }
}
