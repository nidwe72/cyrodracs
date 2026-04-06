package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class EntityProvider implements Coded {

    private Long id;
    private String code;
    private DataFormEntityType entityType;
    private Long entityTypeNodeId;
    private FilterNode filter;
    private String filterInjectableRef;
    private Long filterInjectableRefNodeId;
    private List<SortField> sortFields = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public String getCode() { return code; }
    @Override
    public void setCode(String code) { this.code = code; }

    public DataFormEntityType getEntityType() { return entityType; }
    public void setEntityType(DataFormEntityType entityType) { this.entityType = entityType; }

    public Long getEntityTypeNodeId() { return entityTypeNodeId; }
    public void setEntityTypeNodeId(Long entityTypeNodeId) { this.entityTypeNodeId = entityTypeNodeId; }

    public FilterNode getFilter() { return filter; }
    public void setFilter(FilterNode filter) { this.filter = filter; }

    public String getFilterInjectableRef() { return filterInjectableRef; }
    public void setFilterInjectableRef(String filterInjectableRef) { this.filterInjectableRef = filterInjectableRef; }

    public Long getFilterInjectableRefNodeId() { return filterInjectableRefNodeId; }
    public void setFilterInjectableRefNodeId(Long filterInjectableRefNodeId) { this.filterInjectableRefNodeId = filterInjectableRefNodeId; }

    public List<SortField> getSortFields() { return sortFields; }
    public void setSortFields(List<SortField> sortFields) { this.sortFields = sortFields; }
}
