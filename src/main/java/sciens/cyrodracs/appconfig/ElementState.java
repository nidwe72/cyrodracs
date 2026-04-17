package sciens.cyrodracs.appconfig;

import java.util.List;

public class ElementState {

    private boolean visible = true;
    private List<EntityOption> options;

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public List<EntityOption> getOptions() { return options; }
    public void setOptions(List<EntityOption> options) { this.options = options; }
}
