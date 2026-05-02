package sciens.cyrodracs.appconfig;

/**
 * One resolved dot-path value for a pending row, returned by
 * {@code resolveDotPathValues} per gridElement.md G7.8.
 *
 * <p>{@link #pendingRowIndex} and {@link #columnKey} are echoed from the
 * input so the frontend can match each entry back to its (row, column).</p>
 */
public class ResolvedDotPathValue {

    private int pendingRowIndex;
    private String columnKey;
    /** Resolved leaf entity id, or {@code null} when unreachable. */
    private Long leafId;
    /** Rendered display string (via the column's EntityRenderer Mustache
     *  template), or {@code null} when no rendererRef was supplied. */
    private String displayValue;

    public ResolvedDotPathValue() {}

    public ResolvedDotPathValue(int pendingRowIndex, String columnKey,
                                Long leafId, String displayValue) {
        this.pendingRowIndex = pendingRowIndex;
        this.columnKey = columnKey;
        this.leafId = leafId;
        this.displayValue = displayValue;
    }

    public int getPendingRowIndex() { return pendingRowIndex; }
    public void setPendingRowIndex(int pendingRowIndex) { this.pendingRowIndex = pendingRowIndex; }

    public String getColumnKey() { return columnKey; }
    public void setColumnKey(String columnKey) { this.columnKey = columnKey; }

    public Long getLeafId() { return leafId; }
    public void setLeafId(Long leafId) { this.leafId = leafId; }

    public String getDisplayValue() { return displayValue; }
    public void setDisplayValue(String displayValue) { this.displayValue = displayValue; }
}
