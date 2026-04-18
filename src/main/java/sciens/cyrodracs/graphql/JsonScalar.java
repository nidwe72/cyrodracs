package sciens.cyrodracs.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

import java.util.Locale;

public class JsonScalar {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("JSON")
            .description("Arbitrary JSON value")
            .coercing(new JsonCoercing())
            .build();

    private static class JsonCoercing implements Coercing<Object, Object> {

        @Override
        public Object serialize(Object dataFetcherResult, GraphQLContext context, Locale locale) {
            return dataFetcherResult;
        }

        @Override
        public Object parseValue(Object input, GraphQLContext context, Locale locale) {
            return input;
        }

        @Override
        public Object parseLiteral(Value<?> input, CoercedVariables variables,
                                   GraphQLContext context, Locale locale) {
            return parseLiteralValue(input);
        }

        private Object parseLiteralValue(Value<?> value) {
            if (value instanceof StringValue sv) return sv.getValue();
            if (value instanceof IntValue iv) return iv.getValue().longValue();
            if (value instanceof FloatValue fv) return fv.getValue().doubleValue();
            if (value instanceof BooleanValue bv) return bv.isValue();
            if (value instanceof NullValue) return null;
            if (value instanceof ObjectValue ov) {
                var map = new java.util.LinkedHashMap<String, Object>();
                for (var f : ov.getObjectFields()) {
                    map.put(f.getName(), parseLiteralValue(f.getValue()));
                }
                return map;
            }
            if (value instanceof ArrayValue av)
                return av.getValues().stream().map(this::parseLiteralValue).toList();
            return null;
        }
    }
}
