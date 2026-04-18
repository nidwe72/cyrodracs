package sciens.cyrodracs.graphql;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.List;

/**
 * Provides a custom {@link GraphQlSource} bean that uses the generated SDL string
 * instead of .graphqls files. This makes Spring for GraphQL's auto-configuration
 * back off ({@code @ConditionalOnMissingBean}).
 */
@Configuration
public class GraphQlSourceConfig {

    @Bean
    public GraphQlSource graphQlSource(@Qualifier("graphqlSchemaSDL") String graphqlSchemaSDL,
                                        List<RuntimeWiringConfigurer> configurers) {
        // Parse SDL
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = parser.parse(graphqlSchemaSDL);

        // Build runtime wiring (custom scalars, etc.)
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
        for (var configurer : configurers) {
            configurer.configure(wiringBuilder);
        }
        RuntimeWiring wiring = wiringBuilder.build();

        // Generate executable schema
        SchemaGenerator generator = new SchemaGenerator();
        GraphQLSchema schema = generator.makeExecutableSchema(registry, wiring);

        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        return new GraphQlSource() {
            @Override
            public GraphQL graphQl() { return graphQL; }

            @Override
            public GraphQLSchema schema() { return schema; }
        };
    }
}
