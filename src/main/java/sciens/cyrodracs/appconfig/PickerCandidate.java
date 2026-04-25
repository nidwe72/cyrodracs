package sciens.cyrodracs.appconfig;

/**
 * One candidate row returned by the entity-ref column picker. The label is
 * rendered server-side via the column's EntityRenderer Mustache template,
 * keeping a single source of truth for display strings.
 */
public class PickerCandidate {

    private Long id;
    private String label;

    public PickerCandidate() {}

    public PickerCandidate(Long id, String label) {
        this.id = id;
        this.label = label;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
