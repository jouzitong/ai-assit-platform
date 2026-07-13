package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class FilterEvaluator {
    public boolean test(FilterNode node, Map<String, Object> row) {
        if (node == null) return true;
        return switch (node.getType()) {
            case PREDICATE -> predicate(node, row.get(node.getField()));
            case AND -> children(node).stream().allMatch(child -> test(child, row));
            case OR -> children(node).stream().anyMatch(child -> test(child, row));
            case NOT -> !children(node).stream().allMatch(child -> test(child, row));
        };
    }

    private boolean predicate(FilterNode node, Object actual) {
        Object expected = node.getValue();
        return switch (node.getOperator()) {
            case EQ -> java.util.Objects.equals(normalize(actual), normalize(expected));
            case NE -> !java.util.Objects.equals(normalize(actual), normalize(expected));
            case GT -> compare(actual, expected) > 0;
            case GTE -> compare(actual, expected) >= 0;
            case LT -> compare(actual, expected) < 0;
            case LTE -> compare(actual, expected) <= 0;
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
            case LIKE -> actual != null && expected != null && String.valueOf(actual).contains(String.valueOf(expected));
            case IN -> values(node).stream().map(this::normalize).anyMatch(value -> java.util.Objects.equals(normalize(actual), value));
            case NOT_IN -> values(node).stream().map(this::normalize).noneMatch(value -> java.util.Objects.equals(normalize(actual), value));
        };
    }

    private List<FilterNode> children(FilterNode node) { return node.getChildren() == null ? List.of() : node.getChildren(); }

    private Collection<?> values(FilterNode node) {
        if (node.getValues() != null && !node.getValues().isEmpty()) return node.getValues();
        return node.getValue() instanceof Collection<?> values ? values : List.of();
    }

    private Object normalize(Object value) {
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object left, Object right) {
        if (left == null || right == null) return left == right ? 0 : left == null ? -1 : 1;
        if (left instanceof Number || right instanceof Number) return new BigDecimal(String.valueOf(left)).compareTo(new BigDecimal(String.valueOf(right)));
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) return comparable.compareTo(right);
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
