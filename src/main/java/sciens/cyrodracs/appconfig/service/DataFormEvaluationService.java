package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.expression.EditorEntityBuilder;
import sciens.cyrodracs.expression.ExpressionContext;
import sciens.cyrodracs.expression.ExpressionResolver;

import java.util.*;

@Service
public class DataFormEvaluationService {

    private final AppConfigStore appConfigStore;
    private final ExpressionResolver expressionResolver;
    private final EntitySelectService entitySelectService;
    private final EditorEntityBuilder editorEntityBuilder;
    private final EntityManager entityManager;

    public DataFormEvaluationService(AppConfigStore appConfigStore,
                                     ExpressionResolver expressionResolver,
                                     EntitySelectService entitySelectService,
                                     EditorEntityBuilder editorEntityBuilder,
                                     EntityManager entityManager) {
        this.appConfigStore = appConfigStore;
        this.expressionResolver = expressionResolver;
        this.entitySelectService = entitySelectService;
        this.editorEntityBuilder = editorEntityBuilder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<String, ElementState> evaluate(String dataFormCode, Long entityId,
                                               String changedElement,
                                               Map<String, String> formState) {
        AppConfig config = appConfigStore.getAppConfig();
        if (config == null) {
            throw new IllegalStateException("AppConfig not loaded");
        }

        DataForm form = config.getDataForms().get(dataFormCode);
        if (form == null) {
            throw new IllegalArgumentException("DataForm not found: " + dataFormCode);
        }

        List<DataFormElement> toEvaluate;
        if (changedElement == null) {
            // Initial load: evaluate all elements with visibility or provider
            toEvaluate = form.getElements().values().stream()
                    .filter(el -> el.getVisibilityRule() != null
                               || el.getEntityProviderRef() != null)
                    .toList();
        } else {
            toEvaluate = resolveAffectedElements(form, changedElement);
        }

        // Build editor entity from formState (same as EntitySelectService)
        Object editorEntity = null;
        if (form.getEntity() != null) {
            Class<?> editorEntityClass = resolveClass(form.getEntity().getFqcn());
            if (formState != null && !formState.isEmpty()) {
                editorEntity = editorEntityBuilder.buildFromFormState(
                        editorEntityClass, entityId, formState);
            } else if (entityId != null) {
                editorEntity = entityManager.find(editorEntityClass, entityId);
            }
        }

        ExpressionContext ctx = new ExpressionContext();
        ctx.put("editor", editorEntity);
        ctx.put("formState", formState != null ? formState : Map.of());

        Map<String, ElementState> result = new LinkedHashMap<>();

        for (DataFormElement el : toEvaluate) {
            ElementState state = new ElementState();

            // Visibility
            if (el.getVisibilityRule() != null && el.getVisibilityRule().getExpressionRef() != null) {
                Boolean visible = expressionResolver.resolveBoolean(
                        el.getVisibilityRule().getExpressionRef(), ctx);
                state.setVisible(visible != null && visible);
            }

            // Options (for ENTITY_SELECT elements with a provider)
            if (el.getEntityProviderRef() != null && el.getEntityRendererRef() != null) {
                List<EntityOption> options = entitySelectService.getOptions(
                        el.getEntityProviderRef(), el.getEntityRendererRef(),
                        dataFormCode, entityId, formState);
                state.setOptions(options);
            }

            result.put(el.getCode(), state);
        }
        return result;
    }

    /**
     * Resolves the transitive closure of elements affected by a change,
     * returned in topological (BFS) order.
     */
    List<DataFormElement> resolveAffectedElements(DataForm form, String changedElement) {
        // Build adjacency map: dependency code → list of dependent elements
        Map<String, List<DataFormElement>> dependents = new HashMap<>();
        for (DataFormElement el : form.getElements().values()) {
            if (el.getReloadOnChangeOf() != null) {
                for (String dep : el.getReloadOnChangeOf()) {
                    dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(el);
                }
            }
        }

        // BFS from changedElement
        List<DataFormElement> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(changedElement);
        visited.add(changedElement);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (DataFormElement dep : dependents.getOrDefault(current, List.of())) {
                if (visited.add(dep.getCode())) {
                    result.add(dep);
                    queue.add(dep.getCode());
                }
            }
        }
        return result;
    }

    private Class<?> resolveClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + fqcn, e);
        }
    }
}
