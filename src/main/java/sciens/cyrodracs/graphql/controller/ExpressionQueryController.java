package sciens.cyrodracs.graphql.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import sciens.cyrodracs.appconfig.ExpressionType;
import sciens.cyrodracs.appconfig.InjectableBaseClass;
import sciens.cyrodracs.expression.CompileCheckService;

import java.util.Map;

@Controller
public class ExpressionQueryController {

    private final CompileCheckService compileCheckService;

    public ExpressionQueryController(CompileCheckService compileCheckService) {
        this.compileCheckService = compileCheckService;
    }

    @QueryMapping
    public Map<String, Object> compileCheckExpression(@Argument Map<String, Object> input) {
        var result = compileCheckService.check(
                ExpressionType.valueOf((String) input.get("type")),
                InjectableBaseClass.valueOf((String) input.get("baseClass")),
                (String) input.get("expression"),
                (String) input.get("expectedEntityType")
        );

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("valid", result.valid());
        response.put("errors", result.errors());
        response.put("warnings", result.warnings());

        if (result.typeContext() != null) {
            var ctx = result.typeContext();
            response.put("typeContext", Map.of(
                    "variables", ctx.variables().entrySet().stream()
                            .map(e -> Map.of("key", e.getKey(), "value", e.getValue()))
                            .toList(),
                    "methods", ctx.methods().entrySet().stream()
                            .map(e -> Map.of("key", e.getKey(), "value", e.getValue()))
                            .toList()
            ));
        }

        return response;
    }
}
