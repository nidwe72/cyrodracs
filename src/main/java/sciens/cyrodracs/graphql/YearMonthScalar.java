package sciens.cyrodracs.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;

import java.time.YearMonth;
import java.util.Locale;

public class YearMonthScalar {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("YearMonth")
            .description("Year and month, e.g. 2024-03")
            .coercing(new YearMonthCoercing())
            .build();

    private static class YearMonthCoercing implements Coercing<YearMonth, String> {

        @Override
        public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale) {
            if (dataFetcherResult instanceof YearMonth ym) return ym.toString();
            return null;
        }

        @Override
        public YearMonth parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof String s) return YearMonth.parse(s);
            throw new CoercingParseValueException("Expected String for YearMonth, got " + input.getClass());
        }

        @Override
        public YearMonth parseLiteral(Value<?> input, CoercedVariables variables,
                                      GraphQLContext context, Locale locale) {
            if (input instanceof StringValue sv) return YearMonth.parse(sv.getValue());
            throw new CoercingParseLiteralException("Expected StringValue for YearMonth");
        }
    }
}
