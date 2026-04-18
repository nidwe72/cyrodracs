package sciens.cyrodracs.graphql;

import graphql.schema.idl.RuntimeWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.appconfig.persistence.AppConfigTypeEntity;
import sciens.cyrodracs.camera.*;
import sciens.cyrodracs.expression.CompileCheckService;
import sciens.cyrodracs.expression.ExpressionTypeResolver;

@Configuration
public class GraphQLSchemaConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphQLSchemaConfig.class);

    @Bean
    public JavaSchemaGenerator javaSchemaGenerator() {
        var gen = new JavaSchemaGenerator();

        // Register all domain types that appear in query/mutation results
        gen.addType(AppConfig.class);
        gen.addType(DataForm.class);
        gen.addType(DataFormElement.class);
        gen.addType(EntityProvider.class);
        gen.addType(EntityRenderer.class);
        gen.addType(ViewNode.class);
        gen.addType(FilterNode.class);
        gen.addType(Expression.class);
        gen.addType(TableColumn.class);
        gen.addType(AddAction.class);
        gen.addType(ContextBinding.class);
        gen.addType(VisibilityRule.class);
        gen.addType(SortField.class);
        gen.addType(EntityOption.class);
        gen.addType(ElementState.class);
        gen.addType(BindingProposalResponse.class);
        gen.addType(BindingCompletion.class);
        gen.addType(DataFormData.class);
        gen.addType(PendingChild.class);
        gen.addType(AppConfigTypeEntity.class);

        // Camera JPA entities
        gen.addType(Camera.class);
        gen.addType(CameraProducer.class);
        gen.addType(CameraLensMount.class);
        gen.addType(CameraLensMount2CameraProducer.class);

        // Expression types
        gen.addType(ExpressionTypeResolver.MethodInfo.class);
        gen.addType(CompileCheckService.CompileCheckResult.class);
        gen.addType(CompileCheckService.CompileError.class);
        gen.addType(CompileCheckService.CompileWarning.class);

        return gen;
    }

    @Bean
    public String graphqlSchemaSDL(JavaSchemaGenerator gen) {
        String sdl = gen.generateSDL(QUERY_SDL, MUTATION_SDL, INPUT_AND_HELPER_TYPES_SDL);
        long lines = sdl.lines().count();
        long types = sdl.lines().filter(l -> l.startsWith("type ") || l.startsWith("enum ") || l.startsWith("input ")).count();
        log.info("Generated GraphQL schema: {} lines, {} types/enums/inputs. Inspect via GraphiQL at /graphiql", lines, types);
        log.debug("Full GraphQL schema:\n{}", sdl);
        return sdl;
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return builder -> builder
                .scalar(JsonScalar.INSTANCE)
                .scalar(YearMonthScalar.INSTANCE);
    }

    // ── Hand-written SDL for Query, Mutation, and Input types ──────────────

    private static final String QUERY_SDL = """
            type Query {
                appConfig: AppConfig
                appConfigTypes: [AppConfigTypeEntity]
                appConfigTypeEnumValues(typeCode: String!): [String]
                dataFormData(dataFormCode: String!, entityId: Int!): DataFormData
                evaluateDataForm(input: EvaluateInput!): EvaluateResult
                bindingProposals(entityType: String!, prefix: String): BindingProposalResponse
                entitySelectOptions(provider: String!, renderer: String!): [EntityOption]
                viewData(viewNodeCode: String!, page: Int, size: Int): PagedResult
                gridData(input: GridDataInput!): PagedResult
                cameras: [Camera]
                cameraProducers: [CameraProducer]
                cameraLensMounts: [CameraLensMount]
                compileCheckExpression(input: CompileCheckInput!): CompileCheckResultView
            }
            """;

    private static final String MUTATION_SDL = """
            type Mutation {
                addAppConfigNode(input: AddNodeInput!): AppConfig
                updateAppConfigNode(id: Int!, input: UpdateNodeInput!): AppConfig
                copyAppConfigNode(id: Int!, newCode: String!): AppConfig
                deleteAppConfigNode(id: Int!): AppConfig
                addDataFormElement(input: AddDataFormElementInput!): AppConfig
                updateDataFormElementFull(input: UpdateDataFormElementFullInput!): AppConfig
                updateDataForm(input: UpdateDataFormInput!): AppConfig
                updateEntityProvider(input: UpdateEntityProviderInput!): AppConfig
                updateEntityRenderer(input: UpdateEntityRendererInput!): AppConfig
                saveDataFormData(input: DataFormDataInput!): DataFormDataResult
                deleteCamera(id: Int!): Boolean
                deleteCameraProducer(id: Int!): Boolean
                deleteCameraLensMount(id: Int!): Boolean
                deleteViewEntity(viewNodeCode: String!, id: Int!): Boolean
                deleteGridEntity(dataFormCode: String!, elementCode: String!, entityId: Int!): Boolean
            }
            """;

    private static final String INPUT_AND_HELPER_TYPES_SDL = """
            input EvaluateInput {
                dataFormCode: String!
                entityId: Int
                changedElement: String
                formState: [MapEntryInput!]
            }
            input GridDataInput {
                dataFormCode: String!
                elementCode: String!
                entityId: Int!
                formState: [MapEntryInput!]
                page: Int
                size: Int
            }
            input MapEntryInput {
                key: String!
                value: String!
            }
            input DataFormDataInput {
                dataFormCode: String!
                entityId: Int
                values: JSON
                pendingChildren: [PendingChildInput]
            }
            input PendingChildInput {
                dataFormCode: String!
                contextBindingTarget: String!
                values: JSON
                pendingChildren: [PendingChildInput]
            }
            input AddNodeInput {
                parentObjectId: Int
                typeCode: String!
                code: String!
                enumValue: String
            }
            input UpdateNodeInput {
                code: String
                enumValue: String
            }
            input AddDataFormElementInput {
                parentFormId: Int!
                code: String!
                type: DataFormElementType
            }
            input UpdateDataFormElementFullInput {
                elementId: Int!
                code: String
                type: DataFormElementType
                dataBinding: String
                entityProviderRef: String
                entityRendererRef: String
            }
            input UpdateDataFormInput {
                formId: Int!
                code: String
                entity: DataFormEntityType
            }
            input UpdateEntityProviderInput {
                providerId: Int!
                code: String
                entityType: DataFormEntityType
            }
            input UpdateEntityRendererInput {
                rendererId: Int!
                code: String
                entityType: DataFormEntityType
                template: String
            }
            input CompileCheckInput {
                type: String!
                baseClass: String!
                expression: String!
                expectedEntityType: String
            }

            type EvaluateResult {
                elements: [ElementStateEntry]
            }
            type ElementStateEntry {
                key: String!
                value: ElementState
            }
            type PagedResult {
                items: [JSON]
                totalCount: Int
                page: Int
                pageSize: Int
                totalPages: Int
            }
            type DataFormDataResult {
                success: Boolean!
                error: String
                dataFormCode: String
                data: DataFormData
            }
            type CompileCheckResultView {
                valid: Boolean
                errors: [CompileError]
                warnings: [CompileWarning]
                typeContext: TypeContextView
            }
            type TypeContextView {
                variables: [StringMapEntry]
                methods: [MethodInfoListEntry]
            }
            type StringMapEntry {
                key: String!
                value: String!
            }
            type MethodInfoListEntry {
                key: String!
                value: [MethodInfo]
            }
            """;
}
