package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.BindingProposalResponse;
import sciens.cyrodracs.appconfig.DataFormEntityType;
import sciens.cyrodracs.appconfig.EntityOption;
import sciens.cyrodracs.appconfig.service.DataBindingService;
import sciens.cyrodracs.appconfig.service.EntitySelectService;

import java.util.List;

@Controller
public class EntityQueryController {

    private final EntitySelectService entitySelectService;
    private final DataBindingService dataBindingService;

    public EntityQueryController(EntitySelectService entitySelectService,
                                  DataBindingService dataBindingService) {
        this.entitySelectService = entitySelectService;
        this.dataBindingService = dataBindingService;
    }

    @QueryMapping
    public List<EntityOption> entitySelectOptions(@Argument String provider, @Argument String renderer) {
        return entitySelectService.getOptions(provider, renderer);
    }

    @QueryMapping
    public BindingProposalResponse bindingProposals(@Argument String entityType, @Argument String prefix) {
        return dataBindingService.getProposals(
                DataFormEntityType.valueOf(entityType),
                prefix != null ? prefix : "");
    }
}
