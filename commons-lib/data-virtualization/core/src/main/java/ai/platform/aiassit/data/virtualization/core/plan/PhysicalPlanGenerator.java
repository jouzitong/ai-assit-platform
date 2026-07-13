package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.routing.BindingRouter;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PhysicalPlanGenerator {
    private final BindingRouter bindingRouter;
    private final PhysicalSqlRenderer sqlRenderer;
    private final DbAccessService dbAccessService;

    public PhysicalPlanGenerator(BindingRouter bindingRouter, PhysicalSqlRenderer sqlRenderer, DbAccessService dbAccessService) {
        this.bindingRouter = bindingRouter;
        this.sqlRenderer = sqlRenderer;
        this.dbAccessService = dbAccessService;
    }

    public PhysicalExecutionPlan generate(CatalogSnapshot snapshot, VirtualLogicalPlan logicalPlan) {
        String planId = UUID.randomUUID().toString();
        List<PhysicalExecutionPlan.PhysicalTask> tasks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int sequence = 0;
        for (BindingRouter.RoutingDecision decision : bindingRouter.route(snapshot, logicalPlan)) {
            CatalogSnapshot.Binding binding = decision.binding();
            List<CatalogSnapshot.TransformRule> rules = resolveRules(snapshot, binding, logicalPlan.requiredFields());
            Map<Long, String> aliases = aliases(rules);
            DbAccessDbType dbType = dbAccessService.getDbType(binding.sourceKey());
            PhysicalSqlRenderer.Rendered rendered = sqlRenderer.render(snapshot, binding, dbType, logicalPlan, rules, aliases);
            if (!rendered.filterPushed() && logicalPlan.filter() != null) {
                if (!logicalPlan.allowLocalTransform()) {
                    throw new VirtualDataException("FIELD_TRANSFORM_PUSHDOWN_UNSUPPORTED", "过滤条件无法安全下推且请求禁止本地计算");
                }
                warnings.add(binding.code() + " 的过滤条件将在应用层执行，受 maxScanRows 预算约束");
            }
            tasks.add(new PhysicalExecutionPlan.PhysicalTask(
                    planId + "-" + (++sequence), binding, dbType.name(), rendered.sql(), rendered.parameters(),
                    rendered.countOnly() ? 1 : fetchLimit(logicalPlan.maxScanRows()), rendered.filterPushed(), rendered.countOnly(),
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
            CatalogSnapshot.TransformRule rule = snapshot.readableRule(binding.id(), field.id());
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

    private int fetchLimit(int maxRows) {
        return maxRows == Integer.MAX_VALUE ? maxRows : maxRows + 1;
    }
}
