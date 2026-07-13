package ai.platform.aiassit.data.virtualization.core.routing;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.ConsistencyLevel;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RoutingStrategy;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.VirtualLogicalPlan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class BindingRouter {
    public List<RoutingDecision> route(CatalogSnapshot snapshot, VirtualLogicalPlan plan) {
        Map<String, List<CatalogSnapshot.Binding>> groups = new LinkedHashMap<>();
        snapshot.bindings().stream().filter(CatalogSnapshot.Binding::enabled)
                .forEach(binding -> groups.computeIfAbsent(binding.group(), ignored -> new ArrayList<>()).add(binding));
        List<RoutingDecision> decisions = new ArrayList<>();
        for (Map.Entry<String, List<CatalogSnapshot.Binding>> entry : groups.entrySet()) {
            CatalogSnapshot.Binding primary = entry.getValue().stream()
                    .filter(item -> item.role() == BindingRole.PRIMARY).findFirst().orElse(null);
            if (primary == null || !primary.readable() || !matches(primary.routingConfig(), plan.filter())) continue;
            CatalogSnapshot.Binding selected = selectReadBinding(entry.getValue(), primary, plan.consistency());
            decisions.add(new RoutingDecision(selected, routeReason(primary.routingConfig(), plan.filter(), selected)));
        }
        if (decisions.isEmpty()) {
            throw new VirtualDataException("ROUTE_NOT_FOUND", "没有匹配的可读物理绑定: " + snapshot.entityCode());
        }
        if (decisions.size() > plan.maxPhysicalTasks()) {
            throw new VirtualDataException("ROUTE_FANOUT_LIMIT_EXCEEDED", "物理任务数超过预算: " + decisions.size());
        }
        return decisions;
    }

    public CatalogSnapshot.Binding routeWrite(CatalogSnapshot snapshot, Map<String, Object> record, FilterNode filter) {
        List<CatalogSnapshot.Binding> matches = snapshot.bindings().stream()
                .filter(CatalogSnapshot.Binding::enabled).filter(CatalogSnapshot.Binding::writable)
                .filter(item -> item.role() == BindingRole.PRIMARY)
                .filter(item -> matches(item.routingConfig(), record, filter)).toList();
        if (matches.size() != 1) {
            throw new VirtualDataException("NO_WRITABLE_BINDING", "写请求必须唯一命中一个可写主绑定，实际命中: " + matches.size());
        }
        return matches.get(0);
    }

    private CatalogSnapshot.Binding selectReadBinding(
            List<CatalogSnapshot.Binding> group,
            CatalogSnapshot.Binding primary,
            ConsistencyLevel consistency
    ) {
        if (consistency != ConsistencyLevel.EVENTUAL) return primary;
        return group.stream().filter(CatalogSnapshot.Binding::enabled).filter(CatalogSnapshot.Binding::readable)
                .filter(item -> item.role() == BindingRole.REPLICA)
                .max(Comparator.comparingInt(CatalogSnapshot.Binding::readWeight).thenComparing(CatalogSnapshot.Binding::code))
                .orElse(primary);
    }

    private boolean matches(BindingRoutingConfig config, FilterNode filter) {
        return matches(config, Map.of(), filter);
    }

    private boolean matches(BindingRoutingConfig config, Map<String, Object> record, FilterNode filter) {
        if (config == null || config.getStrategy() == null || config.getStrategy() == RoutingStrategy.SINGLE) return true;
        if (config.getShardFields() == null || config.getShardFields().isEmpty()) return false;
        String shardField = config.getShardFields().get(0);
        Object value = record.get(shardField);
        ExactConstraint exact = value == null ? exactConstraint(filter, shardField) : ExactConstraint.of(value);
        if (!exact.known()) {
            if (config.getStrategy() == RoutingStrategy.RANGE) {
                RangeConstraint requestedRange = rangeConstraint(filter, shardField);
                if (requestedRange.known()) return intersects(config.getRange(), requestedRange);
            }
            return record.isEmpty();
        }
        return switch (config.getStrategy()) {
            case HASH -> config.getHash() != null && config.getHash().getModulus() != null
                    && exact.values().stream().anyMatch(item -> Math.floorMod(routeHash(item), config.getHash().getModulus())
                    == config.getHash().getRemainder());
            case LIST -> config.getList() != null && config.getList().getValues() != null
                    && exact.values().stream().anyMatch(item -> config.getList().getValues().stream()
                    .anyMatch(configured -> routeEquals(configured, item)));
            case RANGE -> config.getRange() != null && exact.values().stream().anyMatch(item -> inRange(item, config.getRange()));
            default -> true;
        };
    }

    private boolean inRange(Object value, BindingRoutingConfig.RangeRouteConfig range) {
        boolean lowerMatch = range.getLower() == null || (Boolean.TRUE.equals(range.getLowerInclusive())
                ? compareRoute(value, range.getLower()) >= 0 : compareRoute(value, range.getLower()) > 0);
        boolean upperMatch = range.getUpper() == null || (Boolean.TRUE.equals(range.getUpperInclusive())
                ? compareRoute(value, range.getUpper()) <= 0 : compareRoute(value, range.getUpper()) < 0);
        return lowerMatch && upperMatch;
    }

    private boolean intersects(BindingRoutingConfig.RangeRouteConfig binding, RangeConstraint query) {
        if (binding == null) return false;
        if (binding.getUpper() != null && query.lower() != null) {
            int compared = compareRoute(binding.getUpper(), query.lower());
            if (compared < 0 || compared == 0 && (!Boolean.TRUE.equals(binding.getUpperInclusive()) || !query.lowerInclusive())) {
                return false;
            }
        }
        if (query.upper() != null && binding.getLower() != null) {
            int compared = compareRoute(query.upper(), binding.getLower());
            if (compared < 0 || compared == 0 && (!query.upperInclusive() || !Boolean.TRUE.equals(binding.getLowerInclusive()))) {
                return false;
            }
        }
        return true;
    }

    private ExactConstraint exactConstraint(FilterNode node, String field) {
        if (node == null || node.getType() == null || node.getType() == FilterType.NOT) return ExactConstraint.unknown();
        if (node.getType() == FilterType.PREDICATE) {
            if (!field.equals(node.getField())) return ExactConstraint.unknown();
            if (node.getOperator() == FilterOperator.EQ) return ExactConstraint.of(node.getValue());
            if (node.getOperator() == FilterOperator.IN) {
                Collection<?> values = node.getValues() == null || node.getValues().isEmpty()
                        ? node.getValue() instanceof Collection<?> collection ? collection : List.of()
                        : node.getValues();
                return values.isEmpty() ? ExactConstraint.unknown() : new ExactConstraint(true, new ArrayList<>(values));
            }
            return ExactConstraint.unknown();
        }
        List<FilterNode> children = node.getChildren() == null ? List.of() : node.getChildren();
        if (node.getType() == FilterType.AND) {
            return children.stream().map(child -> exactConstraint(child, field)).filter(ExactConstraint::known)
                    .findFirst().orElseGet(ExactConstraint::unknown);
        }
        if (node.getType() == FilterType.OR) {
            List<ExactConstraint> constraints = children.stream().map(child -> exactConstraint(child, field)).toList();
            if (constraints.isEmpty() || constraints.stream().anyMatch(item -> !item.known())) return ExactConstraint.unknown();
            Set<Object> values = new LinkedHashSet<>();
            constraints.forEach(item -> values.addAll(item.values()));
            return new ExactConstraint(true, new ArrayList<>(values));
        }
        return ExactConstraint.unknown();
    }

    private RangeConstraint rangeConstraint(FilterNode node, String field) {
        if (node == null || node.getType() == null || node.getType() == FilterType.NOT || node.getType() == FilterType.OR) {
            return RangeConstraint.unknown();
        }
        if (node.getType() == FilterType.PREDICATE) {
            if (!field.equals(node.getField()) || node.getOperator() == null) return RangeConstraint.unknown();
            return switch (node.getOperator()) {
                case GT -> new RangeConstraint(true, node.getValue(), false, null, false);
                case GTE -> new RangeConstraint(true, node.getValue(), true, null, false);
                case LT -> new RangeConstraint(true, null, false, node.getValue(), false);
                case LTE -> new RangeConstraint(true, null, false, node.getValue(), true);
                default -> RangeConstraint.unknown();
            };
        }
        RangeConstraint merged = RangeConstraint.unknown();
        for (FilterNode child : node.getChildren() == null ? List.<FilterNode>of() : node.getChildren()) {
            RangeConstraint candidate = rangeConstraint(child, field);
            if (candidate.known()) merged = merge(merged, candidate);
        }
        return merged;
    }

    private RangeConstraint merge(RangeConstraint left, RangeConstraint right) {
        if (!left.known()) return right;
        Object lower = left.lower();
        boolean lowerInclusive = left.lowerInclusive();
        if (right.lower() != null && (lower == null || compareRoute(right.lower(), lower) > 0)) {
            lower = right.lower();
            lowerInclusive = right.lowerInclusive();
        } else if (right.lower() != null && compareRoute(right.lower(), lower) == 0) {
            lowerInclusive = lowerInclusive && right.lowerInclusive();
        }
        Object upper = left.upper();
        boolean upperInclusive = left.upperInclusive();
        if (right.upper() != null && (upper == null || compareRoute(right.upper(), upper) < 0)) {
            upper = right.upper();
            upperInclusive = right.upperInclusive();
        } else if (right.upper() != null && compareRoute(right.upper(), upper) == 0) {
            upperInclusive = upperInclusive && right.upperInclusive();
        }
        return new RangeConstraint(true, lower, lowerInclusive, upper, upperInclusive);
    }

    private boolean routeEquals(Object left, Object right) {
        if (left == null || right == null) return left == right;
        if (left instanceof Number || right instanceof Number) {
            try { return decimal(left).compareTo(decimal(right)) == 0; }
            catch (NumberFormatException ignored) { return Objects.equals(String.valueOf(left), String.valueOf(right)); }
        }
        return Objects.equals(left, right) || String.valueOf(left).equals(String.valueOf(right));
    }

    private int routeHash(Object value) {
        if (value == null) return 0;
        String canonical;
        try { canonical = decimal(value).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException ignored) { canonical = String.valueOf(value); }
        return canonical.hashCode();
    }

    private int compareRoute(Object left, Object right) {
        try { return decimal(left).compareTo(decimal(right)); }
        catch (NumberFormatException ignored) { return String.valueOf(left).compareTo(String.valueOf(right)); }
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private String routeReason(BindingRoutingConfig config, FilterNode filter, CatalogSnapshot.Binding selected) {
        String strategy = config == null || config.getStrategy() == null ? "SINGLE" : config.getStrategy().name();
        return strategy + " route; selected " + selected.role() + " binding " + selected.code();
    }

    public record RoutingDecision(CatalogSnapshot.Binding binding, String reason) {
    }

    private record ExactConstraint(boolean known, List<Object> values) {
        static ExactConstraint unknown() { return new ExactConstraint(false, List.of()); }
        static ExactConstraint of(Object value) {
            List<Object> values = new ArrayList<>();
            values.add(value);
            return new ExactConstraint(true, values);
        }
    }

    private record RangeConstraint(boolean known, Object lower, boolean lowerInclusive, Object upper, boolean upperInclusive) {
        static RangeConstraint unknown() { return new RangeConstraint(false, null, false, null, false); }
    }
}
