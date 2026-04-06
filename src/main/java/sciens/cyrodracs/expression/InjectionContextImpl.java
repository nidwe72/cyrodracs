package sciens.cyrodracs.expression;

import java.util.Collections;
import java.util.Map;

public class InjectionContextImpl implements InjectionContext {

    private final Object editorEntity;
    private final Map<String, String> formState;
    private final Map<String, String> routeParams;
    private final Map<String, String> sessionData;

    public InjectionContextImpl(Object editorEntity,
                                 Map<String, String> formState,
                                 Map<String, String> routeParams,
                                 Map<String, String> sessionData) {
        this.editorEntity = editorEntity;
        this.formState = formState != null ? Collections.unmodifiableMap(formState) : Map.of();
        this.routeParams = routeParams != null ? Collections.unmodifiableMap(routeParams) : Map.of();
        this.sessionData = sessionData != null ? Collections.unmodifiableMap(sessionData) : Map.of();
    }

    @Override
    public <T> T getEditorEntity(Class<T> entityClass) {
        if (editorEntity == null) return null;
        return entityClass.cast(editorEntity);
    }

    @Override
    public Object getEditorEntity() {
        return editorEntity;
    }

    @Override
    public Map<String, String> getFormState() { return formState; }

    @Override
    public String getFormValue(String dataBindingPath) { return formState.get(dataBindingPath); }

    @Override
    public Map<String, String> getRouteParams() { return routeParams; }

    @Override
    public String getRouteParam(String name) { return routeParams.get(name); }

    @Override
    public Map<String, String> getSessionData() { return sessionData; }

    @Override
    public String getSessionValue(String key) { return sessionData.get(key); }
}
