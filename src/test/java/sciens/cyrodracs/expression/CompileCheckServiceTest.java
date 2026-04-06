package sciens.cyrodracs.expression;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;
import sciens.cyrodracs.expression.CompileCheckService.CompileCheckResult;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CompileCheckServiceTest {

    @Autowired CompileCheckService compileCheckService;

    @Test
    void validClassCompiles() {
        CompileCheckResult result = compileCheckService.check(
                ExpressionType.INJECTABLE_CLASS,
                InjectableBaseClass.FILTER,
                """
                @Override
                public void execute() {
                    setResult(comparison("id", FilterOperator.EQUALS, 1L));
                }""",
                null);

        assertTrue(result.valid(), "Valid INJECTABLE_CLASS should compile");
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validSnippetCompiles() {
        CompileCheckResult result = compileCheckService.check(
                ExpressionType.INJECTABLE_SNIPPET,
                InjectableBaseClass.FILTER,
                "setResult(comparison(\"id\", FilterOperator.EQUALS, 1L));",
                null);

        assertTrue(result.valid(), "Valid INJECTABLE_SNIPPET should compile");
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void syntaxErrorReturnsLineNumber() {
        CompileCheckResult result = compileCheckService.check(
                ExpressionType.INJECTABLE_CLASS,
                InjectableBaseClass.FILTER,
                """
                @Override
                public void execute() {
                    setResul(null);
                }""",
                null);

        assertFalse(result.valid(), "Syntax error should fail compilation");
        assertFalse(result.errors().isEmpty(), "Should have at least one error");

        var error = result.errors().get(0);
        assertTrue(error.line() > 0, "Error should have a positive line number, got: " + error.line());
        assertTrue(error.message().contains("setResul"),
                "Error message should mention the misspelled method, got: " + error.message());
    }

    @Test
    void entityTypeMismatchProducesWarning() {
        CompileCheckResult result = compileCheckService.check(
                ExpressionType.INJECTABLE_CLASS,
                InjectableBaseClass.FILTER,
                """
                @Override
                public void execute() {
                    Object p = getInjectionContext().getEditorEntity(Camera.class);
                    setResult(null);
                }""",
                "CAMERA_PRODUCER");

        assertTrue(result.valid(), "Should compile successfully");
        assertFalse(result.warnings().isEmpty(), "Should have entity type mismatch warning");
        assertTrue(result.warnings().get(0).message().contains("Camera.class"),
                "Warning should mention Camera.class");
        assertTrue(result.warnings().get(0).message().contains("CAMERA_PRODUCER"),
                "Warning should mention expected entity type");
    }

    @Test
    void matchingEntityTypeNoWarning() {
        CompileCheckResult result = compileCheckService.check(
                ExpressionType.INJECTABLE_CLASS,
                InjectableBaseClass.FILTER,
                """
                @Override
                public void execute() {
                    Object p = getInjectionContext().getEditorEntity(CameraProducer.class);
                    setResult(null);
                }""",
                "CAMERA_PRODUCER");

        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty(), "Matching entity type should produce no warning");
    }

    @Test
    void scaffoldLineCountsAreCorrect() {
        // Verify the computed line counts match expectations
        assertTrue(InjectableSourceBuilder.CLASS_SCAFFOLD_LINES > 0,
                "CLASS_SCAFFOLD_LINES should be positive");
        assertTrue(InjectableSourceBuilder.SNIPPET_SCAFFOLD_LINES > InjectableSourceBuilder.CLASS_SCAFFOLD_LINES,
                "SNIPPET should have more scaffold lines than CLASS (adds @Override + execute)");
    }
}
