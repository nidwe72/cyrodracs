package sciens.cyrodracs.appconfig;

import java.util.List;

public class BindingProposalResponse {

    private String entityLabel;
    private List<BindingCompletion> completions;

    public BindingProposalResponse() {}

    public BindingProposalResponse(String entityLabel, List<BindingCompletion> completions) {
        this.entityLabel = entityLabel;
        this.completions = completions;
    }

    public String getEntityLabel() { return entityLabel; }
    public void setEntityLabel(String entityLabel) { this.entityLabel = entityLabel; }

    public List<BindingCompletion> getCompletions() { return completions; }
    public void setCompletions(List<BindingCompletion> completions) { this.completions = completions; }
}
