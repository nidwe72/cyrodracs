package sciens.cyrodracs.expression.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;
import sciens.cyrodracs.expression.CompileCheckService;
import sciens.cyrodracs.expression.CompileCheckService.CompileCheckResult;

@RestController
@RequestMapping("/api/expressions")
@CrossOrigin(origins = "*")
public class CompileCheckController {

    private final CompileCheckService compileCheckService;

    public CompileCheckController(CompileCheckService compileCheckService) {
        this.compileCheckService = compileCheckService;
    }

    @PostMapping("/compile-check")
    public ResponseEntity<CompileCheckResult> compileCheck(@RequestBody CompileCheckRequest request) {
        try {
            ExpressionType type = ExpressionType.valueOf(request.type());
            InjectableBaseClass baseClass = InjectableBaseClass.valueOf(request.baseClass());

            CompileCheckResult result = compileCheckService.check(
                    type, baseClass, request.expression(), request.expectedEntityType());

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record CompileCheckRequest(
            String type,
            String baseClass,
            String expression,
            String expectedEntityType
    ) {}
}
