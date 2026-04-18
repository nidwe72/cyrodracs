package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.*;

import java.util.Collection;

/**
 * SchemaMapping resolvers for AppConfig fields that need Map→List conversion.
 * Spring for GraphQL's default property fetcher would return the Map directly,
 * but the schema declares these as lists.
 */
@Controller
public class AppConfigSchemaController {

    @SchemaMapping(typeName = "AppConfig", field = "dataForms")
    public Collection<DataForm> dataForms(AppConfig config) {
        return config.getDataForms().values();
    }

    @SchemaMapping(typeName = "AppConfig", field = "entityProviders")
    public Collection<EntityProvider> entityProviders(AppConfig config) {
        return config.getEntityProviders().values();
    }

    @SchemaMapping(typeName = "AppConfig", field = "entityRenderers")
    public Collection<EntityRenderer> entityRenderers(AppConfig config) {
        return config.getEntityRenderers().values();
    }

    @SchemaMapping(typeName = "AppConfig", field = "viewTree")
    public Collection<ViewNode> viewTree(AppConfig config) {
        return config.getViewTree().values();
    }

    @SchemaMapping(typeName = "AppConfig", field = "expressions")
    public Collection<Expression> expressions(AppConfig config) {
        return config.getExpressions().values();
    }

    @SchemaMapping(typeName = "DataForm", field = "elements")
    public Collection<DataFormElement> elements(DataForm form) {
        return form.getElements().values();
    }
}
