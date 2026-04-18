package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.AppConfig;
import sciens.cyrodracs.appconfig.AppConfigStore;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeRepository;

import java.util.Arrays;
import java.util.List;

@Controller
public class AppConfigQueryController {

    private final AppConfigStore store;
    private final AppConfigTypeRepository typeRepo;

    public AppConfigQueryController(AppConfigStore store, AppConfigTypeRepository typeRepo) {
        this.store = store;
        this.typeRepo = typeRepo;
    }

    @QueryMapping
    public AppConfig appConfig() {
        return store.getAppConfig();
    }

    @QueryMapping
    public List<AppConfigTypeEntity> appConfigTypes() {
        return typeRepo.findAll();
    }

    @QueryMapping
    public List<String> appConfigTypeEnumValues(@Argument String typeCode) {
        var type = typeRepo.findByCode(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + typeCode));
        if (!type.isEnumType() || type.getJavaType() == null) {
            throw new IllegalArgumentException("Type " + typeCode + " is not an enum type");
        }
        try {
            Class<?> enumClass = Class.forName(type.getJavaType());
            return Arrays.stream(enumClass.getEnumConstants())
                    .map(c -> ((Enum<?>) c).name())
                    .toList();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Enum class not found: " + type.getJavaType(), e);
        }
    }
}
