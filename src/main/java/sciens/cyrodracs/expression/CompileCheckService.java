package sciens.cyrodracs.expression;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.SimpleCompiler;
import org.springframework.stereotype.Service;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompileCheckService {

    public record CompileError(int line, String message) {}
    public record CompileWarning(String message) {}

    public record CompileCheckResult(
            boolean valid,
            List<CompileError> errors,
            List<CompileWarning> warnings
    ) {
        public static CompileCheckResult success(List<CompileWarning> warnings) {
            return new CompileCheckResult(true, List.of(), warnings);
        }

        public static CompileCheckResult failure(List<CompileError> errors) {
            return new CompileCheckResult(false, errors, List.of());
        }
    }

    public CompileCheckResult check(ExpressionType type, InjectableBaseClass baseClass,
                                     String expression, String expectedEntityType) {
        boolean isClassBody = (type == ExpressionType.INJECTABLE_CLASS);
        String source = isClassBody
                ? InjectableSourceBuilder.buildClassSource("$CompileCheck", baseClass, expression)
                : InjectableSourceBuilder.buildSnippetSource("$CompileCheck", baseClass, expression);

        List<CompileError> errors = compileOnly(source, isClassBody);
        if (!errors.isEmpty()) {
            return CompileCheckResult.failure(errors);
        }

        List<CompileWarning> warnings = new ArrayList<>();
        if (expectedEntityType != null && !expectedEntityType.isEmpty()) {
            checkEntityTypeMismatch(expression, expectedEntityType, warnings);
        }
        return CompileCheckResult.success(warnings);
    }

    private List<CompileError> compileOnly(String source, boolean isClassBody) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.setParentClassLoader(getClass().getClassLoader());
            compiler.cook(source);
            return List.of();
        } catch (CompileException e) {
            Location loc = e.getLocation();
            int line = loc != null ? loc.getLineNumber() : -1;
            int scaffoldLines = isClassBody
                    ? InjectableSourceBuilder.CLASS_SCAFFOLD_LINES
                    : InjectableSourceBuilder.SNIPPET_SCAFFOLD_LINES;
            int userLine = line > 0 ? line - scaffoldLines : -1;
            String message = e.getMessage();
            // Strip the location prefix that Janino adds (e.g., "Line 12, Column 5: ...")
            int colonIdx = message.indexOf(": ");
            if (colonIdx > 0 && message.substring(0, colonIdx).matches(".*\\d.*")) {
                message = message.substring(colonIdx + 2);
            }
            return List.of(new CompileError(userLine, message));
        } catch (Exception e) {
            return List.of(new CompileError(-1, e.getMessage()));
        }
    }

    private void checkEntityTypeMismatch(String source, String expectedEntityType,
                                          List<CompileWarning> warnings) {
        Pattern pattern = Pattern.compile("getEditorEntity\\s*\\(\\s*(\\w+)\\.class\\s*\\)");
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String usedClass = matcher.group(1);
            String expectedSimple = toPascalCase(expectedEntityType);
            if (!usedClass.equals(expectedSimple)) {
                warnings.add(new CompileWarning(
                        "Source calls getEditorEntity(" + usedClass + ".class) but this expression "
                        + "is used with entity type " + expectedEntityType + ". "
                        + "Expected getEditorEntity(" + expectedSimple + ".class)."));
            }
        }
    }

    private static String toPascalCase(String screamingSnake) {
        StringBuilder sb = new StringBuilder();
        for (String part : screamingSnake.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
