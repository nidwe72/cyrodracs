package sciens.cyrodracs.appconfig.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.stereotype.Service;
import sciens.cyrodracs.appconfig.BindingCompletion;
import sciens.cyrodracs.appconfig.BindingProposalResponse;
import sciens.cyrodracs.appconfig.DataFormEntityType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataBindingService {

    private final EntityManager entityManager;

    private static final Map<Class<?>, String> TYPE_TO_ELEMENT = Map.ofEntries(
            Map.entry(String.class, "INPUT_STRING"),
            Map.entry(Long.class, "INPUT_NUMBER"),
            Map.entry(long.class, "INPUT_NUMBER"),
            Map.entry(Integer.class, "INPUT_NUMBER"),
            Map.entry(int.class, "INPUT_NUMBER"),
            Map.entry(Double.class, "INPUT_NUMBER"),
            Map.entry(double.class, "INPUT_NUMBER"),
            Map.entry(Float.class, "INPUT_NUMBER"),
            Map.entry(float.class, "INPUT_NUMBER"),
            Map.entry(Boolean.class, "CHECKBOX"),
            Map.entry(boolean.class, "CHECKBOX"),
            Map.entry(YearMonth.class, "DATE_PICKER__YEAR_MONTH"),
            Map.entry(LocalDate.class, "DATE_PICKER"),
            Map.entry(LocalDateTime.class, "DATE_TIME_PICKER"),
            Map.entry(LocalTime.class, "TIME_PICKER")
    );

    public DataBindingService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public BindingProposalResponse getProposals(DataFormEntityType entityType, String prefix) {
        Class<?> rootClass = resolveClass(entityType);
        Class<?> targetClass = walkPrefix(rootClass, prefix);

        EntityType<?> metamodel = entityManager.getMetamodel().entity(targetClass);
        List<BindingCompletion> completions = new ArrayList<>();

        for (SingularAttribute<?, ?> attr : metamodel.getSingularAttributes()) {
            if ("id".equals(attr.getName())) continue;

            Attribute.PersistentAttributeType pat = attr.getPersistentAttributeType();

            if (pat == Attribute.PersistentAttributeType.BASIC) {
                Class<?> javaType = attr.getJavaType();
                String suggested = TYPE_TO_ELEMENT.getOrDefault(javaType, "INPUT_STRING");
                completions.add(new BindingCompletion(
                        attr.getName(),
                        javaType.getSimpleName(),
                        true,
                        suggested
                ));
            }
            // Non-leaf (MANY_TO_ONE) will be added in Task 2
        }

        return new BindingProposalResponse(targetClass.getSimpleName(), completions);
    }

    private Class<?> resolveClass(DataFormEntityType entityType) {
        try {
            return Class.forName(entityType.getFqcn());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Entity class not found: " + entityType.getFqcn(), e);
        }
    }

    /**
     * Walks a dot-separated prefix path through the JPA metamodel to resolve
     * the target entity class. An empty or null prefix returns the root class.
     */
    private Class<?> walkPrefix(Class<?> rootClass, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return rootClass;
        }

        Class<?> current = rootClass;
        String[] segments = prefix.split("\\.");

        for (String segment : segments) {
            if (segment.isBlank()) continue;

            EntityType<?> metamodel = entityManager.getMetamodel().entity(current);
            SingularAttribute<?, ?> attr = metamodel.getSingularAttribute(segment);

            if (attr.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_ONE
                    && attr.getPersistentAttributeType() != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                throw new IllegalArgumentException(
                        "Cannot navigate into non-entity attribute '" + segment
                                + "' on " + current.getSimpleName());
            }

            current = attr.getJavaType();
        }

        return current;
    }
}
