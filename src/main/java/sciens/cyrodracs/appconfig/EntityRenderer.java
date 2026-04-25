package sciens.cyrodracs.appconfig;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderer implements Coded {

    private Long id;
    private String code;
    private DataFormEntityType entityType;
    private Long entityTypeNodeId;
    private String template;
    private Long templateNodeId;
    private List<String> searchFields = new ArrayList<>();
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

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public Long getTemplateNodeId() { return templateNodeId; }
    public void setTemplateNodeId(Long templateNodeId) { this.templateNodeId = templateNodeId; }

    public List<String> getSearchFields() { return searchFields; }
    public void setSearchFields(List<String> searchFields) { this.searchFields = searchFields; }

    public List<SortField> getSortFields() { return sortFields; }
    public void setSortFields(List<SortField> sortFields) { this.sortFields = sortFields; }
}
