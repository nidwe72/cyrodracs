package sciens.cyrodracs.expression;

import java.util.Map;

/**
 * Read-only API available to injectable expressions.
 * This is the ONLY object injected into snippet/class execution —
 * injectables cannot access EntityManager, Spring beans, or anything else.
 */
public interface InjectionContext {

    /**
     * The entity currently in the editor, as a typed instance.
     * Built from form state (reflects unsaved edits) with @ManyToOne
     * relationships loaded from DB for full navigation.
     *
     * The returned entity is TRANSIENT — not JPA-managed, not attached to
     * any persistence context. Calling setters has no effect on the database.
     *
     * Returns null in "create new" mode before any data exists.
     */
    <T> T getEditorEntity(Class<T> entityClass);

    /** Non-generic overload for Janino compatibility. Returns the raw editor entity. */
    Object getEditorEntity();

    /** Current form field values as a read-only map. */
    Map<String, String> getFormState();

    /** Get a single form field value by its dataBinding path. */
    String getFormValue(String dataBindingPath);

    /** Route parameters (e.g., viewNodeCode, URL query params). */
    Map<String, String> getRouteParams();

    String getRouteParam(String name);

    /** Session-level data (current user, locale, etc.). */
    Map<String, String> getSessionData();

    String getSessionValue(String key);
}
