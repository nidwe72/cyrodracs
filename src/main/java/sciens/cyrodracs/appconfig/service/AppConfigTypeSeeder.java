package sciens.cyrodracs.appconfig.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigObjectRepository;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;

/**
 * Seeds APP_CONFIG_TYPE rows (the schema) and an initial root APP_CONFIG_OBJECT
 * row if the tables are empty. Runs once at startup before AppConfigStore loads.
 */
@Component
public class AppConfigTypeSeeder {

    private final AppConfigTypeRepository typeRepo;
    private final AppConfigObjectRepository objectRepo;

    public AppConfigTypeSeeder(AppConfigTypeRepository typeRepo, AppConfigObjectRepository objectRepo) {
        this.typeRepo = typeRepo;
        this.objectRepo = objectRepo;
    }

    @PostConstruct
    @Transactional
    public void seedIfNeeded() {
        seedTypes();
        seedDefaultRootIfAbsent();
    }

    private void seedTypes() {
        AppConfigTypeEntity appConfigType = ensureType("AppConfig", null, null, false, false,
                "sciens.cyrodracs.appconfig.AppConfig");

        AppConfigTypeEntity dataFormType = ensureType("DataForm", appConfigType, "dataForms", true, false,
                "sciens.cyrodracs.appconfig.DataForm");

        AppConfigTypeEntity dataFormElementType = ensureType("DataFormElement", dataFormType, "elements", true, false,
                "sciens.cyrodracs.appconfig.DataFormElement");

        ensureType("DataFormElementType", dataFormElementType, "type", false, true,
                "sciens.cyrodracs.appconfig.DataFormElementType");

        ensureType("DataBinding", dataFormElementType, "dataBinding", false, false,
                "java.lang.String");

        ensureType("EntityProviderRef", dataFormElementType, "entityProviderRef", false, false,
                "java.lang.String");

        ensureType("EntityRendererRef", dataFormElementType, "entityRendererRef", false, false,
                "java.lang.String");

        ensureType("DataFormEntityType", dataFormType, "entity", false, true,
                "sciens.cyrodracs.appconfig.DataFormEntityType");

        // EntityProvider types
        AppConfigTypeEntity entityProviderType = ensureType("EntityProvider", appConfigType, "entityProviders", true, false,
                "sciens.cyrodracs.appconfig.EntityProvider");

        ensureType("EntityProviderEntityType", entityProviderType, "entityType", false, true,
                "sciens.cyrodracs.appconfig.DataFormEntityType");

        // FilterNode types (child of EntityProvider)
        AppConfigTypeEntity filterNodeType = ensureType("FilterNode", entityProviderType, "filter", false, false,
                "sciens.cyrodracs.appconfig.FilterNode");

        ensureType("FilterNodeType", filterNodeType, "type", false, true,
                "sciens.cyrodracs.appconfig.FilterNodeType");

        ensureType("FilterField", filterNodeType, "field", false, false,
                "java.lang.String");

        ensureType("FilterOperator", filterNodeType, "operator", false, true,
                "sciens.cyrodracs.appconfig.FilterOperator");

        ensureType("FilterValue", filterNodeType, "value", false, false,
                "java.lang.String");

        ensureType("FilterValueItem", filterNodeType, "values", true, false,
                "java.lang.String");

        // FilterNode children (recursive: child FilterNodes for AND_GROUP / OR_GROUP)
        ensureType("FilterNodeChildren", filterNodeType, "children", true, false,
                "sciens.cyrodracs.appconfig.FilterNode");

        // SortField types (child of EntityProvider)
        AppConfigTypeEntity sortFieldType = ensureType("SortField", entityProviderType, "sortFields", true, false,
                "sciens.cyrodracs.appconfig.SortField");

        ensureType("SortFieldField", sortFieldType, "field", false, false,
                "java.lang.String");

        ensureType("SortDirection", sortFieldType, "direction", false, true,
                "sciens.cyrodracs.appconfig.SortDirection");

        // EntityRenderer types
        AppConfigTypeEntity entityRendererType = ensureType("EntityRenderer", appConfigType, "entityRenderers", true, false,
                "sciens.cyrodracs.appconfig.EntityRenderer");

        ensureType("EntityRendererEntityType", entityRendererType, "entityType", false, true,
                "sciens.cyrodracs.appconfig.DataFormEntityType");

        ensureType("EntityRendererTemplate", entityRendererType, "template", false, false,
                "java.lang.String");

        // ViewTree types
        AppConfigTypeEntity viewNodeType = ensureType("ViewNode", appConfigType, "viewTree", true, false,
                "sciens.cyrodracs.appconfig.ViewNode");

        ensureType("ViewNodeType", viewNodeType, "type", false, true,
                "sciens.cyrodracs.appconfig.ViewNodeType");

        ensureType("ViewNodeLabel", viewNodeType, "label", false, false,
                "java.lang.String");

        ensureType("ViewNodeProviderRef", viewNodeType, "entityProviderRef", false, false,
                "java.lang.String");

        ensureType("ViewNodeDataFormRef", viewNodeType, "dataFormRef", false, false,
                "java.lang.String");

        ensureType("ViewNodeContent", viewNodeType, "content", false, false,
                "java.lang.String");

        // ViewNode children (recursive: parent is ViewNode, child type is also ViewNode)
        ensureType("ViewNodeChildren", viewNodeType, "children", true, false,
                "sciens.cyrodracs.appconfig.ViewNode");

        // TableColumn types
        AppConfigTypeEntity tableColumnType = ensureType("TableColumn", viewNodeType, "tableColumns", true, false,
                "sciens.cyrodracs.appconfig.TableColumn");

        ensureType("TableColumnKey", tableColumnType, "key", false, false,
                "java.lang.String");

        ensureType("TableColumnHeader", tableColumnType, "header", false, false,
                "java.lang.String");

        ensureType("TableColumnRendererRef", tableColumnType, "entityRendererRef", false, false,
                "java.lang.String");

        // GRID TableColumns on DataFormElement (reuses TableColumn model)
        AppConfigTypeEntity gridTableColumnType = ensureType("GridTableColumn", dataFormElementType, "tableColumns", true, false,
                "sciens.cyrodracs.appconfig.TableColumn");

        ensureType("GridTableColumnKey", gridTableColumnType, "key", false, false,
                "java.lang.String");

        ensureType("GridTableColumnHeader", gridTableColumnType, "header", false, false,
                "java.lang.String");

        ensureType("GridTableColumnRendererRef", gridTableColumnType, "entityRendererRef", false, false,
                "java.lang.String");

        // AddAction on DataFormElement (generic: used by GRID, reusable by other element types)
        AppConfigTypeEntity addActionType = ensureType("AddAction", dataFormElementType, "addAction", false, false,
                "sciens.cyrodracs.appconfig.AddAction");

        ensureType("AddActionTarget", addActionType, "targetDataFormRef", false, false,
                "java.lang.String");

        ensureType("AddActionLabel", addActionType, "childLabel", false, false,
                "java.lang.String");

        // ContextBinding on AddAction (generic: target/source pair for context injection)
        AppConfigTypeEntity contextBindingType = ensureType("ContextBinding", addActionType, "contextBindings", true, false,
                "sciens.cyrodracs.appconfig.ContextBinding");

        ensureType("ContextBindingTarget", contextBindingType, "target", false, false,
                "java.lang.String");

        ensureType("ContextBindingSource", contextBindingType, "source", false, false,
                "java.lang.String");

        // ReloadOnChange on DataFormElement
        ensureType("ReloadOnChange", dataFormElementType, "reloadOnChange", false, false,
                "java.lang.Boolean");

        // VisibilityRule on DataFormElement
        AppConfigTypeEntity visibilityRuleType = ensureType("VisibilityRule", dataFormElementType, "visibilityRule", false, false,
                "sciens.cyrodracs.appconfig.VisibilityRule");

        ensureType("VisibilityExpressionRef", visibilityRuleType, "expressionRef", false, false,
                "java.lang.String");

        ensureType("VisibilityOperator", visibilityRuleType, "operator", false, true,
                "sciens.cyrodracs.appconfig.VisibilityOperator");

        ensureType("VisibilityCompareValue", visibilityRuleType, "compareValue", false, false,
                "java.lang.String");

        // FilterNode.expressionRef
        ensureType("FilterExpressionRef", filterNodeType, "expressionRef", false, false,
                "java.lang.String");

        // EntityProvider.filterInjectableRef
        ensureType("FilterInjectableRef", entityProviderType, "filterInjectableRef", false, false,
                "java.lang.String");

        // Expression types
        AppConfigTypeEntity expressionType = ensureType("Expression", appConfigType, "expressions", true, false,
                "sciens.cyrodracs.appconfig.Expression");

        ensureType("ExpressionType", expressionType, "type", false, true,
                "sciens.cyrodracs.appconfig.ExpressionType");

        ensureType("ExpressionBody", expressionType, "expression", false, false,
                "java.lang.String");

        ensureType("InjectableBaseClass", expressionType, "baseClass", false, true,
                "sciens.cyrodracs.appconfig.InjectableBaseClass");

        ensureType("ExpressionDescription", expressionType, "description", false, false,
                "java.lang.String");
    }

    private AppConfigTypeEntity ensureType(String code, AppConfigTypeEntity parent,
                                           String fieldName, boolean collection,
                                           boolean enumType, String javaType) {
        return typeRepo.findByCode(code).orElseGet(() -> {
            AppConfigTypeEntity t = new AppConfigTypeEntity();
            t.setCode(code);
            t.setParentType(parent);
            t.setFieldName(fieldName);
            t.setCollection(collection);
            t.setEnumType(enumType);
            t.setJavaType(javaType);
            return typeRepo.save(t);
        });
    }

    private void seedDefaultRootIfAbsent() {
        if (objectRepo.count() > 0) return;

        AppConfigTypeEntity appConfigType = typeRepo.findByCode("AppConfig")
                .orElseThrow(() -> new IllegalStateException("AppConfig type not found after seeding"));

        AppConfigObjectEntity root = new AppConfigObjectEntity();
        root.setType(appConfigType);
        root.setCode("default");
        objectRepo.save(root);
    }
}
