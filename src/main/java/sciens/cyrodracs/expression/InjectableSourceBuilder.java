package sciens.cyrodracs.expression;

import sciens.cyrodracs.appconfig.InjectableBaseClass;

/**
 * Builds compilable Java source for injectable expressions.
 * Single source of truth for the scaffold (package, imports, class declaration)
 * used by both InjectableExecutor (runtime) and CompileCheckService (validation).
 */
public final class InjectableSourceBuilder {

    private InjectableSourceBuilder() {}

    private static final String PACKAGE_AND_IMPORTS = """
            package sciens.cyrodracs.expression.generated;

            import sciens.cyrodracs.expression.*;
            import sciens.cyrodracs.appconfig.*;
            import sciens.cyrodracs.camera.*;
            import java.util.*;
            import java.time.*;

            """;

    /** The import statements shown in the editor (without package declaration). */
    public static final String IMPORTS_DISPLAY = """
            import sciens.cyrodracs.expression.*;
            import sciens.cyrodracs.appconfig.*;
            import sciens.cyrodracs.camera.*;
            import java.util.*;
            import java.time.*;""";

    /**
     * Number of scaffold lines before user code in an INJECTABLE_CLASS source.
     * Computed from the actual scaffold to stay in sync automatically.
     */
    public static final int CLASS_SCAFFOLD_LINES;

    /**
     * Number of scaffold lines before user code in an INJECTABLE_SNIPPET source.
     * Computed from the actual scaffold to stay in sync automatically.
     */
    public static final int SNIPPET_SCAFFOLD_LINES;

    static {
        // Use a dummy base class to count lines. The line count is the same
        // regardless of which base class is used (same number of lines).
        String classSource = buildClassSource("$Count", InjectableBaseClass.FILTER, "MARKER");
        CLASS_SCAFFOLD_LINES = classSource.substring(0, classSource.indexOf("MARKER"))
                .split("\n", -1).length - 1;

        String snippetSource = buildSnippetSource("$Count", InjectableBaseClass.FILTER, "MARKER");
        SNIPPET_SCAFFOLD_LINES = snippetSource.substring(0, snippetSource.indexOf("MARKER"))
                .split("\n", -1).length - 1;
    }

    /**
     * Builds the full compilable source for an INJECTABLE_CLASS expression.
     * The classBody is the user's code (full method(s) including @Override).
     */
    public static String buildClassSource(String className, InjectableBaseClass baseClass,
                                           String classBody) {
        return PACKAGE_AND_IMPORTS
                + "public class " + className + " extends " + baseClass.getFqcn() + " {\n"
                + "    " + classBody + "\n"
                + "}\n";
    }

    /**
     * Builds the full compilable source for an INJECTABLE_SNIPPET expression.
     * The methodBody is the user's code (statements inside execute()).
     */
    public static String buildSnippetSource(String className, InjectableBaseClass baseClass,
                                             String methodBody) {
        return PACKAGE_AND_IMPORTS
                + "public class " + className + " extends " + baseClass.getFqcn() + " {\n"
                + "    @Override\n"
                + "    public void execute() {\n"
                + "        " + methodBody + "\n"
                + "    }\n"
                + "}\n";
    }
}
