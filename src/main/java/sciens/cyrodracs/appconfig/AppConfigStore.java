package sciens.cyrodracs.appconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import sciens.cyrodracs.appconfig.service.AppConfigTreeBuilder;
import sciens.cyrodracs.appconfig.service.AppConfigTypeSeeder;

/**
 * Application-scoped singleton holding the live AppConfig tree.
 * Loaded once at startup (after the seeder runs) and refreshed after every mutation.
 */
@Component
public class AppConfigStore {

    private final AppConfigTreeBuilder treeBuilder;
    // Injected only to guarantee the seeder runs before this bean's @PostConstruct
    @SuppressWarnings("unused")
    private final AppConfigTypeSeeder seeder;

    private volatile AppConfig appConfig;

    public AppConfigStore(AppConfigTreeBuilder treeBuilder, AppConfigTypeSeeder seeder) {
        this.treeBuilder = treeBuilder;
        this.seeder = seeder;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public void reload() {
        this.appConfig = treeBuilder.buildTree();
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }
}
