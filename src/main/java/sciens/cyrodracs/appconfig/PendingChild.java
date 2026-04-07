package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PendingChild {

    private String dataFormCode;
    private String contextBindingTarget;
    private Map<String, Object> values = new LinkedHashMap<>();
    private List<PendingChild> pendingChildren = new ArrayList<>();

    public String getDataFormCode() { return dataFormCode; }
    public void setDataFormCode(String dataFormCode) { this.dataFormCode = dataFormCode; }

    public String getContextBindingTarget() { return contextBindingTarget; }
    public void setContextBindingTarget(String contextBindingTarget) { this.contextBindingTarget = contextBindingTarget; }

    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }

    public List<PendingChild> getPendingChildren() { return pendingChildren; }
    public void setPendingChildren(List<PendingChild> pendingChildren) { this.pendingChildren = pendingChildren; }
}
