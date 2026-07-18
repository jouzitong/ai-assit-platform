package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.DefaultFieldMappingResolver;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.routing.BindingRouter;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.QueryType;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalProjection;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQueryCommand;
import ai.platform.aiassit.data.virtualization.spi.query.PhysicalQuerySpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PhysicalPlanGenerator {
    private final BindingRouter bindingRouter;
    private final PhysicalFilterMapper filterMapper;
    private final DefaultFieldMappingResolver defaultFieldMappingResolver;

    public PhysicalPlanGenerator(
            BindingRouter bindingRouter,
            PhysicalFilterMapper filterMapper,
            DefaultFieldMappingResolver defaultFieldMappingResolver
    ) {
        this.bindingRouter = bindingRouter;
        this.filterMapper = filterMapper;
        this.defaultFieldMappingResolver = defaultFieldMappingResolver;
    }

    public PhysicalExecutionPlan generate(CatalogSnapshot snapshot, VirtualLogicalPlan logicalPlan) {
        String requestId = UUID.randomUUID().toString();
        String planId = UUID.randomUUID().toString();
        List<PhysicalExecutionPlan.PhysicalTask> tasks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int sequence = 0;
        for (BindingRouter.RoutingDecision decision : bindingRouter.route(snapshot, logicalPlan)) {
            CatalogSnapshot.Binding binding = decision.binding();
            List<CatalogSnapshot.TransformRule> rules = resolveRules(snapshot, binding, logicalPlan.requiredFields());
            Map<Long, String> aliases = aliases(rules);
            Map<String, String> pushdownFields = pushdownFields(snapshot, rules);
            boolean filterPushed = filterMapper.canMap(logicalPlan.filter(), pushdownFields);
            boolean countOnly = logicalPlan.queryType() == QueryType.COUNT && filterPushed;
            if (!filterPushed && logicalPlan.filter() != null) {
                if (!logicalPlan.allowLocalTransform()) {
                    throw new VirtualDataException("FIELD_TRANSFORM_PUSHDOWN_UNSUPPORTED", "过滤条件无法安全下推且请求禁止本地计算");
                }
                warnings.add(binding.code() + " 的过滤条件将在应用层执行，受 maxScanRows 预算约束");
            }
            List<PhysicalProjection> projections = projections(rules, aliases);
            if (!countOnly && projections.isEmpty()) {
                throw new VirtualDataException("FIELD_NOT_MAPPED", "物理查询没有可投影字段");
            }
            int limit = countOnly ? 1 : fetchLimit(logicalPlan.maxScanRows());
            PhysicalFilter physicalFilter = filterPushed ? filterMapper.map(logicalPlan.filter(), pushdownFields) : null;
            String taskId = planId + "-" + (++sequence);
            PhysicalQuerySpec querySpec = new PhysicalQuerySpec(
                    binding.physicalTableName(), projections, physicalFilter, countOnly, limit);
            PhysicalQueryCommand command = new PhysicalQueryCommand(
                    requestId, planId, taskId, binding.sourceKey(), querySpec, limit, logicalPlan.timeoutMs());
            tasks.add(new PhysicalExecutionPlan.PhysicalTask(
                    taskId, binding, command, filterPushed, countOnly,
                    decision.reason(), rules, aliases
            ));
        }
        return new PhysicalExecutionPlan(planId, snapshot, logicalPlan, tasks, warnings);
    }

    private List<CatalogSnapshot.TransformRule> resolveRules(
            CatalogSnapshot snapshot,
            CatalogSnapshot.Binding binding,
            Set<String> requiredFields
    ) {
        Map<Long, CatalogSnapshot.TransformRule> rules = new LinkedHashMap<>();
        for (String fieldCode : requiredFields) {
            CatalogSnapshot.VirtualField field = snapshot.fieldsByCode().get(fieldCode);
            CatalogSnapshot.TransformRule rule = defaultFieldMappingResolver.resolveReadableRule(snapshot, binding, field);
            if (rule == null) {
                throw new VirtualDataException("FIELD_NOT_MAPPED", "绑定 " + binding.code() + " 缺少字段读取规则: " + fieldCode);
            }
            rules.put(rule.id(), rule);
        }
        return rules.values().stream().sorted(Comparator.comparing(CatalogSnapshot.TransformRule::code)).toList();
    }

    private Map<Long, String> aliases(List<CatalogSnapshot.TransformRule> rules) {
        Map<Long, String> aliases = new LinkedHashMap<>();
        rules.forEach(rule -> rule.physicalPorts().forEach(port -> aliases.putIfAbsent(port.physicalFieldMetaId(), "__p" + port.physicalFieldMetaId())));
        return aliases;
    }

    private List<PhysicalProjection> projections(
            List<CatalogSnapshot.TransformRule> rules,
            Map<Long, String> aliases
    ) {
        Map<Long, CatalogSnapshot.Port> ports = new LinkedHashMap<>();
        rules.forEach(rule -> rule.physicalPorts().forEach(port -> ports.putIfAbsent(port.physicalFieldMetaId(), port)));
        return ports.entrySet().stream()
                .map(entry -> new PhysicalProjection(entry.getValue().physicalColumnName(), aliases.get(entry.getKey())))
                .toList();
    }

    private Map<String, String> pushdownFields(
            CatalogSnapshot snapshot,
            List<CatalogSnapshot.TransformRule> rules
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        for (CatalogSnapshot.TransformRule rule : rules) {
            if (!"identity".equals(rule.readTransformerCode())
                    || rule.physicalPorts().size() != 1
                    || rule.virtualPorts().size() != 1) {
                continue;
            }
            CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(rule.virtualPorts().get(0).virtualFieldId());
            if (field != null) result.put(field.code(), rule.physicalPorts().get(0).physicalColumnName());
        }
        return result;
    }

    private int fetchLimit(int maxRows) {
        return maxRows == Integer.MAX_VALUE ? maxRows : maxRows + 1;
    }
}
