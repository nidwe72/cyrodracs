package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Component;
import sciens.cyrodracs.appconfig.EntityProvider;
import sciens.cyrodracs.appconfig.FilterNode;
import sciens.cyrodracs.appconfig.FilterNodeType;
import sciens.cyrodracs.appconfig.SortField;
import sciens.cyrodracs.appconfig.SortDirection;
import sciens.cyrodracs.expression.ExpressionContext;
import sciens.cyrodracs.expression.ExpressionResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import jakarta.persistence.TypedQuery;

/**
 * Shared utility that executes EntityProvider queries with filter and sort
 * using JPA Criteria API.
 */
@Component
public class FilterExecutor {

    private final EntityManager entityManager;
    private ExpressionResolver expressionResolver;

    public FilterExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Setter injection to avoid circular dependency (ExpressionResolver → AppConfigStore → FilterExecutor). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExpressionResolver(ExpressionResolver expressionResolver) {
        this.expressionResolver = expressionResolver;
    }

    /** Holds a page of results together with the total count across all pages. */
    public record PagedResult(List<?> items, long totalCount) {}

    /**
     * Queries all entities matching the provider's filter and sort configuration.
     */
    public List<?> executeQuery(EntityProvider provider, Class<?> entityClass) {
        return executePagedQuery(provider, entityClass, 0, Integer.MAX_VALUE).items();
    }

    /**
     * Queries a page of entities matching the provider's filter and sort configuration.
     *
     * @param offset zero-based first-result index
     * @param limit  maximum number of results to return
     */
    public PagedResult executePagedQuery(EntityProvider provider, Class<?> entityClass,
                                         int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // --- count query ---
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.count(countRoot));
        if (provider.getFilter() != null) {
            Predicate countPredicate = buildPredicate(provider.getFilter(), countRoot, cb);
            if (countPredicate != null) {
                countQuery.where(countPredicate);
            }
        }
        long totalCount = entityManager.createQuery(countQuery).getSingleResult();

        // --- data query ---
        CriteriaQuery<Object> dataQuery = cb.createQuery(Object.class);
        Root<?> root = dataQuery.from(entityClass);
        dataQuery.select(root);

        if (provider.getFilter() != null) {
            Predicate predicate = buildPredicate(provider.getFilter(), root, cb);
            if (predicate != null) {
                dataQuery.where(predicate);
            }
        }

        if (!provider.getSortFields().isEmpty()) {
            List<Order> orders = provider.getSortFields().stream()
                    .map(sf -> {
                        Path<?> path = walkPath(root, sf.getField());
                        return sf.getDirection() == SortDirection.DESC
                                ? cb.desc(path) : cb.asc(path);
                    })
                    .toList();
            dataQuery.orderBy(orders);
        }

        TypedQuery<Object> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);

        return new PagedResult(typedQuery.getResultList(), totalCount);
    }

    /**
     * Execute with dynamic expression context. Merges static filter + injectable filter.
     */
    public PagedResult executePagedQuery(EntityProvider provider, Class<?> entityClass,
                                         int offset, int limit,
                                         ExpressionContext context) {
        FilterNode staticFilter = resolveFilterExpressions(provider.getFilter(), context);

        FilterNode injectableFilter = null;
        if (provider.getFilterInjectableRef() != null && expressionResolver != null) {
            injectableFilter = expressionResolver.resolveFilter(
                provider.getFilterInjectableRef(), context);
        }

        FilterNode effectiveFilter = mergeFilters(staticFilter, injectableFilter);

        // Create a temporary provider with the resolved effective filter
        EntityProvider resolvedProvider = new EntityProvider();
        resolvedProvider.setEntityType(provider.getEntityType());
        resolvedProvider.setFilter(effectiveFilter);
        resolvedProvider.setSortFields(provider.getSortFields());

        return executePagedQuery(resolvedProvider, entityClass, offset, limit);
    }

    private FilterNode mergeFilters(FilterNode staticFilter, FilterNode injectableFilter) {
        if (staticFilter == null && injectableFilter == null) return null;
        if (staticFilter == null) return injectableFilter;
        if (injectableFilter == null) return staticFilter;

        FilterNode merged = new FilterNode();
        merged.setType(FilterNodeType.AND_GROUP);
        merged.setChildren(List.of(staticFilter, injectableFilter));
        return merged;
    }

    private FilterNode resolveFilterExpressions(FilterNode node, ExpressionContext context) {
        if (node == null || expressionResolver == null) return node;
        if (node.getExpressionRef() != null) {
            String resolved = expressionResolver.resolve(node.getExpressionRef(), context);
            FilterNode copy = copyFilterNode(node);
            copy.setValue(resolved);
            copy.setExpressionRef(null);
            return copy;
        }
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            FilterNode copy = copyFilterNode(node);
            copy.setChildren(node.getChildren().stream()
                .map(child -> resolveFilterExpressions(child, context))
                .toList());
            return copy;
        }
        return node;
    }

    private FilterNode copyFilterNode(FilterNode source) {
        FilterNode copy = new FilterNode();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setType(source.getType());
        copy.setTypeNodeId(source.getTypeNodeId());
        copy.setField(source.getField());
        copy.setFieldNodeId(source.getFieldNodeId());
        copy.setOperator(source.getOperator());
        copy.setOperatorNodeId(source.getOperatorNodeId());
        copy.setValue(source.getValue());
        copy.setValueNodeId(source.getValueNodeId());
        copy.setValues(source.getValues());
        copy.setExpressionRef(source.getExpressionRef());
        copy.setExpressionRefNodeId(source.getExpressionRefNodeId());
        copy.setChildren(source.getChildren());
        return copy;
    }

    private Predicate buildPredicate(FilterNode node, Root<?> root, CriteriaBuilder cb) {
        if (node.getType() == null) return null;

        if (node.getType() == FilterNodeType.COMPARISON) {
            return buildComparison(node, root, cb);
        }

        // AND_GROUP or OR_GROUP
        List<Predicate> childPredicates = node.getChildren().stream()
                .map(child -> buildPredicate(child, root, cb))
                .filter(p -> p != null)
                .toList();

        if (childPredicates.isEmpty()) return null;

        Predicate[] arr = childPredicates.toArray(new Predicate[0]);
        return node.getType() == FilterNodeType.AND_GROUP
                ? cb.and(arr)
                : cb.or(arr);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildComparison(FilterNode node, Root<?> root, CriteriaBuilder cb) {
        if (node.getField() == null || node.getOperator() == null) return null;

        Path<?> path = walkPath(root, node.getField());
        Class<?> javaType = path.getJavaType();

        return switch (node.getOperator()) {
            case EQUALS -> cb.equal(path, convertValue(node.getValue(), javaType));
            case NOT_EQUALS -> cb.notEqual(path, convertValue(node.getValue(), javaType));
            case GREATER_THAN -> cb.greaterThan((Path<Comparable>) path,
                    (Comparable) convertValue(node.getValue(), javaType));
            case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo((Path<Comparable>) path,
                    (Comparable) convertValue(node.getValue(), javaType));
            case LESS_THAN -> cb.lessThan((Path<Comparable>) path,
                    (Comparable) convertValue(node.getValue(), javaType));
            case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo((Path<Comparable>) path,
                    (Comparable) convertValue(node.getValue(), javaType));
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            case IN -> path.in(node.getValues().stream()
                    .map(v -> convertValue(v, javaType))
                    .toList());
            case LIKE -> cb.like(path.as(String.class), node.getValue());
        };
    }

    /**
     * Walks a dot-separated path through JPA joins.
     * E.g., "producer.name" becomes root.join("producer").get("name").
     */
    private Path<?> walkPath(Root<?> root, String dotPath) {
        String[] segments = dotPath.split("\\.");
        if (segments.length == 1) {
            return root.get(segments[0]);
        }

        // Join through relationships for all segments except the last
        Join<?, ?> join = root.join(segments[0], JoinType.LEFT);
        for (int i = 1; i < segments.length - 1; i++) {
            join = join.join(segments[i], JoinType.LEFT);
        }
        return join.get(segments[segments.length - 1]);
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == String.class) return value;
        if (targetType == Long.class || targetType == long.class) return Long.valueOf(value);
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(value);
        if (targetType == Double.class || targetType == double.class) return Double.valueOf(value);
        if (targetType == Float.class || targetType == float.class) return Float.valueOf(value);
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(value);
        if (targetType == YearMonth.class) {
            String s = value;
            if (s.length() > 7 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                s = s.substring(0, 7);
            }
            return YearMonth.parse(s);
        }
        if (targetType == LocalDate.class) return LocalDate.parse(value);
        if (targetType == LocalDateTime.class) return LocalDateTime.parse(value);
        return value;
    }
}
