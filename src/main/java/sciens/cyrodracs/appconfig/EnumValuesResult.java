package sciens.cyrodracs.appconfig;

import java.util.List;

/**
 * Response DTO for {@code enumValuesForColumn} per columnFilters.md
 * CF3.4.5. Carries the dropdown's option set for an ENUM column —
 * either the restricted DISTINCT-from-rows result (flag true) or the
 * full declared-constants list (flag false). Frontend renders the
 * dropdown options from {@link #values} and treats an empty list as
 * the *"No values match the current filters."* state.
 */
public class EnumValuesResult {

    private List<String> values;

    public EnumValuesResult() {}

    public EnumValuesResult(List<String> values) {
        this.values = values;
    }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
