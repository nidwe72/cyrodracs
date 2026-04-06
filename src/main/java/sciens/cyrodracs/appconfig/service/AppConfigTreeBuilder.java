package sciens.cyrodracs.appconfig.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.DataForm;
import sciens.cyrodracs.appconfig.DataFormElement;
import sciens.cyrodracs.appconfig.DataFormElementType;
import sciens.cyrodracs.appconfig.DataFormEntityType;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.EntityRenderer;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.FilterOperator;
import sciens.cyrodracs.appconfig.SortDirection;
import sciens.cyrodracs.appconfig.SortField;
import sciens.cyrodracs.appconfig.TableColumn;
import sciens.cyrodracs.appconfig.ViewNode;
import sciens.cyrodracs.appconfig.ViewNodeType;
import sciens.cyrodracs.appconfig.Expression;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;
import sciens.cyrodracs.appconfig.VisibilityRule;
import sciens.cyrodracs.appconfig.VisibilityOperator;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppConfigTreeBuilder {

    private final AppConfigObjectRepository objectRepo;

    public AppConfigTreeBuilder(AppConfigObjectRepository objectRepo) {
        this.objectRepo = objectRepo;
    }

    @Transactional(readOnly = true)
    public AppConfig buildTree() {
        List<AppConfigObjectEntity> all = objectRepo.findAll();
        if (all.isEmpty()) return null;

        Map<Long, List<AppConfigObjectEntity>> childrenByParentId = all.stream()
                .filter(o -> o.getParentObjectId() != null)
                .collect(Collectors.groupingBy(AppConfigObjectEntity::getParentObjectId));

        AppConfigObjectEntity root = all.stream()
                .filter(o -> o.getParentObjectId() == null)
                .findFirst()
                .orElse(null);

        if (root == null) return null;

        return buildAppConfig(root, childrenByParentId);
    }

    private AppConfig buildAppConfig(AppConfigObjectEntity entity,
                                     Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        AppConfig config = new AppConfig();
        config.setId(entity.getId());
        config.setCode(entity.getCode());

        Map<String, DataForm> dataForms = new LinkedHashMap<>();
        Map<String, EntityProvider> entityProviders = new LinkedHashMap<>();
        Map<String, EntityRenderer> entityRenderers = new LinkedHashMap<>();
        Map<String, ViewNode> viewTree = new LinkedHashMap<>();
        Map<String, Expression> expressions = new LinkedHashMap<>();

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String typeCode = child.getType().getCode();
            if ("DataForm".equals(typeCode)) {
                DataForm form = buildDataForm(child, childrenByParentId);
                dataForms.put(form.getCode(), form);
            } else if ("EntityProvider".equals(typeCode)) {
                EntityProvider provider = buildEntityProvider(child, childrenByParentId);
                entityProviders.put(provider.getCode(), provider);
            } else if ("EntityRenderer".equals(typeCode)) {
                EntityRenderer renderer = buildEntityRenderer(child, childrenByParentId);
                entityRenderers.put(renderer.getCode(), renderer);
            } else if ("ViewNode".equals(typeCode)) {
                ViewNode viewNode = buildViewNode(child, childrenByParentId);
                viewTree.put(viewNode.getCode(), viewNode);
            } else if ("Expression".equals(typeCode)) {
                Expression expr = buildExpression(child, childrenByParentId);
                expressions.put(expr.getCode(), expr);
            }
        }
        config.setDataForms(dataForms);
        config.setEntityProviders(entityProviders);
        config.setEntityRenderers(entityRenderers);
        config.setViewTree(viewTree);
        config.setExpressions(expressions);
        return config;
    }

    private DataForm buildDataForm(AppConfigObjectEntity entity,
                                   Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        DataForm form = new DataForm();
        form.setId(entity.getId());
        form.setCode(entity.getCode());

        Map<String, DataFormElement> elements = new LinkedHashMap<>();
        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            if ("DataFormElement".equals(child.getType().getCode())) {
                DataFormElement element = buildDataFormElement(child, childrenByParentId);
                elements.put(element.getCode(), element);
            } else if ("DataFormEntityType".equals(child.getType().getCode()) && child.getEnumValue() != null) {
                form.setEntity(DataFormEntityType.valueOf(child.getEnumValue()));
                form.setEntityNodeId(child.getId());
            }
        }
        form.setElements(elements);
        return form;
    }

    private DataFormElement buildDataFormElement(AppConfigObjectEntity entity,
                                                 Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        DataFormElement element = new DataFormElement();
        element.setId(entity.getId());
        element.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("DataFormElementType".equals(childTypeCode) && child.getEnumValue() != null) {
                element.setType(DataFormElementType.valueOf(child.getEnumValue()));
                element.setTypeNodeId(child.getId());
            } else if ("DataBinding".equals(childTypeCode)) {
                element.setDataBinding(child.getCode());
                element.setDataBindingNodeId(child.getId());
            } else if ("EntityProviderRef".equals(childTypeCode)) {
                element.setEntityProviderRef(child.getCode());
                element.setEntityProviderRefNodeId(child.getId());
            } else if ("EntityRendererRef".equals(childTypeCode)) {
                element.setEntityRendererRef(child.getCode());
                element.setEntityRendererRefNodeId(child.getId());
            } else if ("GridTableColumn".equals(childTypeCode)) {
                element.getTableColumns().add(buildGridTableColumn(child, childrenByParentId));
            } else if ("ReloadOnChange".equals(childTypeCode)) {
                element.setReloadOnChange("true".equalsIgnoreCase(child.getCode()));
                element.setReloadOnChangeNodeId(child.getId());
            } else if ("VisibilityRule".equals(childTypeCode)) {
                element.setVisibilityRule(buildVisibilityRule(child, childrenByParentId));
            }
        }
        return element;
    }

    private EntityProvider buildEntityProvider(AppConfigObjectEntity entity,
                                               Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        EntityProvider provider = new EntityProvider();
        provider.setId(entity.getId());
        provider.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("EntityProviderEntityType".equals(childTypeCode) && child.getEnumValue() != null) {
                provider.setEntityType(DataFormEntityType.valueOf(child.getEnumValue()));
                provider.setEntityTypeNodeId(child.getId());
            } else if ("FilterNode".equals(childTypeCode)) {
                provider.setFilter(buildFilterNode(child, childrenByParentId));
            } else if ("FilterInjectableRef".equals(childTypeCode)) {
                provider.setFilterInjectableRef(child.getCode());
                provider.setFilterInjectableRefNodeId(child.getId());
            } else if ("SortField".equals(childTypeCode)) {
                provider.getSortFields().add(buildSortField(child, childrenByParentId));
            }
        }
        return provider;
    }

    private FilterNode buildFilterNode(AppConfigObjectEntity entity,
                                        Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        FilterNode node = new FilterNode();
        node.setId(entity.getId());
        node.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("FilterNodeType".equals(childTypeCode) && child.getEnumValue() != null) {
                node.setType(FilterNodeType.valueOf(child.getEnumValue()));
                node.setTypeNodeId(child.getId());
            } else if ("FilterField".equals(childTypeCode)) {
                node.setField(child.getCode());
                node.setFieldNodeId(child.getId());
            } else if ("FilterOperator".equals(childTypeCode) && child.getEnumValue() != null) {
                node.setOperator(FilterOperator.valueOf(child.getEnumValue()));
                node.setOperatorNodeId(child.getId());
            } else if ("FilterValue".equals(childTypeCode)) {
                node.setValue(child.getCode());
                node.setValueNodeId(child.getId());
            } else if ("FilterValueItem".equals(childTypeCode)) {
                node.getValues().add(child.getCode());
            } else if ("FilterExpressionRef".equals(childTypeCode)) {
                node.setExpressionRef(child.getCode());
                node.setExpressionRefNodeId(child.getId());
            } else if ("FilterNode".equals(childTypeCode)) {
                // Recursive: child FilterNodes for AND_GROUP / OR_GROUP
                node.getChildren().add(buildFilterNode(child, childrenByParentId));
            }
        }
        return node;
    }

    private SortField buildSortField(AppConfigObjectEntity entity,
                                      Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        SortField sortField = new SortField();
        sortField.setId(entity.getId());
        sortField.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("SortFieldField".equals(childTypeCode)) {
                sortField.setField(child.getCode());
                sortField.setFieldNodeId(child.getId());
            } else if ("SortDirection".equals(childTypeCode) && child.getEnumValue() != null) {
                sortField.setDirection(SortDirection.valueOf(child.getEnumValue()));
                sortField.setDirectionNodeId(child.getId());
            }
        }
        return sortField;
    }

    private EntityRenderer buildEntityRenderer(AppConfigObjectEntity entity,
                                                Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        EntityRenderer renderer = new EntityRenderer();
        renderer.setId(entity.getId());
        renderer.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("EntityRendererEntityType".equals(childTypeCode) && child.getEnumValue() != null) {
                renderer.setEntityType(DataFormEntityType.valueOf(child.getEnumValue()));
                renderer.setEntityTypeNodeId(child.getId());
            } else if ("EntityRendererTemplate".equals(childTypeCode)) {
                renderer.setTemplate(child.getCode());
                renderer.setTemplateNodeId(child.getId());
            }
        }
        return renderer;
    }

    private ViewNode buildViewNode(AppConfigObjectEntity entity,
                                    Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        ViewNode node = new ViewNode();
        node.setId(entity.getId());
        node.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("ViewNodeType".equals(childTypeCode) && child.getEnumValue() != null) {
                node.setType(ViewNodeType.valueOf(child.getEnumValue()));
                node.setTypeNodeId(child.getId());
            } else if ("ViewNodeLabel".equals(childTypeCode)) {
                node.setLabel(child.getCode());
                node.setLabelNodeId(child.getId());
            } else if ("ViewNodeProviderRef".equals(childTypeCode)) {
                node.setEntityProviderRef(child.getCode());
                node.setEntityProviderRefNodeId(child.getId());
            } else if ("ViewNodeDataFormRef".equals(childTypeCode)) {
                node.setDataFormRef(child.getCode());
                node.setDataFormRefNodeId(child.getId());
            } else if ("ViewNodeContent".equals(childTypeCode)) {
                node.setContent(child.getCode());
                node.setContentNodeId(child.getId());
            } else if ("ViewNode".equals(childTypeCode)) {
                // Recursive: child ViewNodes (GROUP children)
                node.getChildren().add(buildViewNode(child, childrenByParentId));
            } else if ("TableColumn".equals(childTypeCode)) {
                node.getTableColumns().add(buildTableColumn(child, childrenByParentId));
            }
        }
        return node;
    }

    private TableColumn buildTableColumn(AppConfigObjectEntity entity,
                                          Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        TableColumn column = new TableColumn();
        column.setId(entity.getId());
        column.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("TableColumnKey".equals(childTypeCode)) {
                column.setKey(child.getCode());
                column.setKeyNodeId(child.getId());
            } else if ("TableColumnHeader".equals(childTypeCode)) {
                column.setHeader(child.getCode());
                column.setHeaderNodeId(child.getId());
            } else if ("TableColumnRendererRef".equals(childTypeCode)) {
                column.setEntityRendererRef(child.getCode());
                column.setEntityRendererRefNodeId(child.getId());
            }
        }
        return column;
    }

    private Expression buildExpression(AppConfigObjectEntity entity,
                                       Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        Expression expr = new Expression();
        expr.setId(entity.getId());
        expr.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("ExpressionType".equals(childTypeCode) && child.getEnumValue() != null) {
                expr.setType(ExpressionType.valueOf(child.getEnumValue()));
                expr.setTypeNodeId(child.getId());
            } else if ("ExpressionBody".equals(childTypeCode)) {
                expr.setExpression(child.getCode());
                expr.setExpressionNodeId(child.getId());
            } else if ("InjectableBaseClass".equals(childTypeCode) && child.getEnumValue() != null) {
                expr.setBaseClass(InjectableBaseClass.valueOf(child.getEnumValue()));
                expr.setBaseClassNodeId(child.getId());
            } else if ("ExpressionDescription".equals(childTypeCode)) {
                expr.setDescription(child.getCode());
                expr.setDescriptionNodeId(child.getId());
            }
        }
        return expr;
    }

    private VisibilityRule buildVisibilityRule(AppConfigObjectEntity entity,
                                               Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        VisibilityRule rule = new VisibilityRule();
        rule.setId(entity.getId());
        rule.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("VisibilityExpressionRef".equals(childTypeCode)) {
                rule.setExpressionRef(child.getCode());
                rule.setExpressionRefNodeId(child.getId());
            } else if ("VisibilityOperator".equals(childTypeCode) && child.getEnumValue() != null) {
                rule.setOperator(VisibilityOperator.valueOf(child.getEnumValue()));
                rule.setOperatorNodeId(child.getId());
            } else if ("VisibilityCompareValue".equals(childTypeCode)) {
                rule.setCompareValue(child.getCode());
                rule.setCompareValueNodeId(child.getId());
            }
        }
        return rule;
    }

    private TableColumn buildGridTableColumn(AppConfigObjectEntity entity,
                                              Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        TableColumn column = new TableColumn();
        column.setId(entity.getId());
        column.setCode(entity.getCode());

        for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
            String childTypeCode = child.getType().getCode();
            if ("GridTableColumnKey".equals(childTypeCode)) {
                column.setKey(child.getCode());
                column.setKeyNodeId(child.getId());
            } else if ("GridTableColumnHeader".equals(childTypeCode)) {
                column.setHeader(child.getCode());
                column.setHeaderNodeId(child.getId());
            } else if ("GridTableColumnRendererRef".equals(childTypeCode)) {
                column.setEntityRendererRef(child.getCode());
                column.setEntityRendererRefNodeId(child.getId());
            }
        }
        return column;
    }

    private List<AppConfigObjectEntity> childrenOf(Long parentId,
                                                    Map<Long, List<AppConfigObjectEntity>> childrenByParentId) {
        return childrenByParentId.getOrDefault(parentId, List.of());
    }
}
