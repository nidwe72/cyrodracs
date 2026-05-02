package sciens.cyrodracs.appconfig;

import java.util.List;

public class ColumnFilterMeta {

    private String columnKey;
    private ColumnFilterType filterType;
    private String entityProviderRef;
    private String entityRendererRef;
    private List<String> enumValues;
    /** CF3.4.5 — surfaces the column's restrictByVisibleRows flag to the
     *  admin editor checkbox. Default true. Runtime filter widgets do
     *  NOT read this field (the backend enforces the flag inside its
     *  picker / enum-values endpoints). */
    private boolean restrictByVisibleRows = true;

    public String getColumnKey() { return columnKey; }
    public void setColumnKey(String columnKey) { this.columnKey = columnKey; }

    public ColumnFilterType getFilterType() { return filterType; }
    public void setFilterType(ColumnFilterType filterType) { this.filterType = filterType; }

    public String getEntityProviderRef() { return entityProviderRef; }
    public void setEntityProviderRef(String entityProviderRef) { this.entityProviderRef = entityProviderRef; }

    public String getEntityRendererRef() { return entityRendererRef; }
    public void setEntityRendererRef(String entityRendererRef) { this.entityRendererRef = entityRendererRef; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

    public boolean isRestrictByVisibleRows() { return restrictByVisibleRows; }
    public void setRestrictByVisibleRows(boolean restrictByVisibleRows) { this.restrictByVisibleRows = restrictByVisibleRows; }
}
