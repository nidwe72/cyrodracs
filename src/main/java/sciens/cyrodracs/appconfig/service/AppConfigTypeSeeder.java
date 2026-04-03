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
        if (typeRepo.count() > 0) return;

        AppConfigTypeEntity appConfigType = type("AppConfig", null, null, false, false,
                "sciens.cyrodracs.appconfig.AppConfig");

        AppConfigTypeEntity dataFormType = type("DataForm", appConfigType, "dataForms", true, false,
                "sciens.cyrodracs.appconfig.DataForm");

        AppConfigTypeEntity dataFormElementType = type("DataFormElement", dataFormType, "elements", true, false,
                "sciens.cyrodracs.appconfig.DataFormElement");

        type("DataFormElementType", dataFormElementType, "type", false, true,
                "sciens.cyrodracs.appconfig.DataFormElementType");
    }

    private AppConfigTypeEntity type(String code, AppConfigTypeEntity parent,
                                     String fieldName, boolean collection,
                                     boolean enumType, String javaType) {
        AppConfigTypeEntity t = new AppConfigTypeEntity();
        t.setCode(code);
        t.setParentType(parent);
        t.setFieldName(fieldName);
        t.setCollection(collection);
        t.setEnumType(enumType);
        t.setJavaType(javaType);
        return typeRepo.save(t);
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
