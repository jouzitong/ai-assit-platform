package ai.platform.aiassit.data.virtualization.core.execution;

import ai.platform.aiassit.data.virtualization.api.VirtualCommandGateway;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandResponse;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CommandType;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.TransactionMode;
import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.core.catalog.VirtualCatalogService;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.plan.PhysicalFilterMapper;
import ai.platform.aiassit.data.virtualization.core.routing.BindingRouter;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformerRegistry;
import ai.platform.aiassit.data.virtualization.core.transform.TransformOutputMapper;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommand;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandPort;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandResult;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandSpec;
import ai.platform.aiassit.data.virtualization.spi.command.PhysicalCommandType;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class VirtualDataCommandService implements VirtualCommandGateway {
    private final VirtualCatalogService catalogService;
    private final BindingRouter bindingRouter;
    private final FieldTransformerRegistry transformerRegistry;
    private final PhysicalFilterMapper filterMapper;
    private final PhysicalCommandPort commandPort;
    private final Map<String, VirtualCommandResponse> idempotencyCache = new ConcurrentHashMap<>();

    public VirtualDataCommandService(
            VirtualCatalogService catalogService,
            BindingRouter bindingRouter,
            FieldTransformerRegistry transformerRegistry,
            PhysicalFilterMapper filterMapper,
            PhysicalCommandPort commandPort
    ) {
        this.catalogService = catalogService;
        this.bindingRouter = bindingRouter;
        this.transformerRegistry = transformerRegistry;
        this.filterMapper = filterMapper;
        this.commandPort = commandPort;
    }

    @Override
    public VirtualCommandResponse command(VirtualCommandRequest request) {
        validate(request);
        String idempotencyKey = request.getIdempotencyKey() == null ? null : request.getEntityCode() + ":" + request.getIdempotencyKey();
        if (idempotencyKey != null) return idempotencyCache.computeIfAbsent(idempotencyKey, ignored -> executeCommand(request));
        return executeCommand(request);
    }

    private VirtualCommandResponse executeCommand(VirtualCommandRequest request) {
        CatalogSnapshot snapshot = catalogService.requirePublished(request.getEntityCode(), request.getCatalogVersion());
        String requestId = UUID.randomUUID().toString();
        String planId = UUID.randomUUID().toString();
        List<CommandTask> tasks = plan(snapshot, request, requestId, planId);
        if (tasks.size() > 1 && request.getTransactionMode() != TransactionMode.BEST_EFFORT) {
            throw new VirtualDataException("DISTRIBUTED_ATOMIC_WRITE_UNSUPPORTED", "跨绑定写入仅支持 BEST_EFFORT");
        }
        VirtualCommandResponse response = execute(tasks, request, requestId, planId);
        log.info("virtual data command completed: planId={}, entityCode={}, commandType={}, tasks={}, affectedRows={}, partialSuccess={}",
                planId, request.getEntityCode(), request.getCommandType(), response.getTasks().size(),
                response.getAffectedRows(), response.getPartialSuccess());
        return response;
    }

    private List<CommandTask> plan(
            CatalogSnapshot snapshot,
            VirtualCommandRequest request,
            String requestId,
            String planId
    ) {
        if (request.getCommandType() == CommandType.INSERT) {
            Map<Long, List<Map<String, Object>>> recordsByBinding = new LinkedHashMap<>();
            Map<Long, CatalogSnapshot.Binding> bindings = new LinkedHashMap<>();
            for (Map<String, Object> record : request.getRecords()) {
                CatalogSnapshot.Binding binding = bindingRouter.routeWrite(snapshot, record, null);
                bindings.put(binding.id(), binding);
                recordsByBinding.computeIfAbsent(binding.id(), ignored -> new ArrayList<>()).add(transformWrite(snapshot, binding, record, true));
            }
            List<CommandTask> tasks = new ArrayList<>();
            for (Map.Entry<Long, List<Map<String, Object>>> entry : recordsByBinding.entrySet()) {
                CatalogSnapshot.Binding binding = bindings.get(entry.getKey());
                validateInsertRows(entry.getValue());
                String taskId = planId + "-" + (tasks.size() + 1);
                PhysicalCommandSpec spec = new PhysicalCommandSpec(
                        PhysicalCommandType.INSERT,
                        binding.physicalTableName(),
                        entry.getValue(),
                        Map.of(),
                        null
                );
                tasks.add(new CommandTask(taskId, binding,
                        new PhysicalCommand(requestId, planId, taskId, binding.sourceKey(), spec)));
            }
            return tasks;
        }

        Map<String, Object> record = request.firstRecord();
        CatalogSnapshot.Binding binding = bindingRouter.routeWrite(snapshot, record, request.getFilter());
        if (request.getFilter() == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID",
                    request.getCommandType() + " 必须提供过滤条件");
        }
        Map<String, String> filterFields = identityFilterFields(snapshot, binding);
        PhysicalFilter physicalFilter = filterMapper.map(request.getFilter(), filterFields);
        Map<String, Object> assignments = Map.of();
        if (request.getCommandType() == CommandType.UPDATE) {
            assignments = transformWrite(snapshot, binding, record, false);
            if (assignments.isEmpty()) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "UPDATE 没有可写物理字段");
            }
        }
        String taskId = planId + "-1";
        PhysicalCommandSpec spec = new PhysicalCommandSpec(
                PhysicalCommandType.valueOf(request.getCommandType().name()),
                binding.physicalTableName(),
                List.of(),
                assignments,
                physicalFilter
        );
        return List.of(new CommandTask(taskId, binding,
                new PhysicalCommand(requestId, planId, taskId, binding.sourceKey(), spec)));
    }

    private Map<String, Object> transformWrite(
            CatalogSnapshot snapshot,
            CatalogSnapshot.Binding binding,
            Map<String, Object> record,
            boolean insert
    ) {
        Set<String> recognized = new LinkedHashSet<>();
        Map<String, Object> physical = new LinkedHashMap<>();
        for (CatalogSnapshot.TransformRule rule : snapshot.writableRules(binding.id())) {
            Map<String, Object> inputs = new LinkedHashMap<>();
            boolean touched = false;
            for (CatalogSnapshot.Port port : rule.virtualPorts()) {
                CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(port.virtualFieldId());
                if (field != null && record.containsKey(field.code())) {
                    inputs.put(port.code(), record.get(field.code()));
                    recognized.add(field.code());
                    touched = true;
                }
            }
            if (!touched && !insert) continue;
            if (!touched) {
                List<String> missing = rule.virtualPorts().stream().filter(CatalogSnapshot.Port::requiredOnWrite)
                        .map(CatalogSnapshot.Port::code).toList();
                if (!missing.isEmpty()) {
                    throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "INSERT 缺少必填虚拟端口: " + missing);
                }
                continue;
            }
            FieldTransformer transformer = transformerRegistry.require(rule.writeTransformerCode(), rule.writeTransformerVersion());
            List<String> missing = rule.virtualPorts().stream().filter(CatalogSnapshot.Port::requiredOnWrite)
                    .filter(port -> !inputs.containsKey(port.code()) || inputs.get(port.code()) == null)
                    .map(CatalogSnapshot.Port::code).toList();
            if (!missing.isEmpty()) {
                throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "组合字段缺少必填写回端口: " + missing);
            }
            if (!transformer.capabilities().partialWrite() && inputs.size() != rule.virtualPorts().size()) {
                throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "变换器不支持组合字段部分写回: " + rule.code());
            }
            Map<String, Object> output = TransformOutputMapper.normalizeSnapshot(transformer.write(inputs, rule.writeConfig()), rule.physicalPorts());
            for (CatalogSnapshot.Port port : rule.physicalPorts()) {
                if (physical.containsKey(port.physicalColumnName())) {
                    throw new VirtualDataException("FIELD_TRANSFORM_CONFLICT", "多个规则写入同一物理字段: " + port.physicalColumnName());
                }
                physical.put(port.physicalColumnName(), output.get(port.code()));
            }
        }
        Set<String> unknown = new LinkedHashSet<>(record.keySet());
        unknown.removeAll(recognized);
        if (!unknown.isEmpty()) throw new VirtualDataException("FIELD_TRANSFORM_WRITE_UNSUPPORTED", "字段没有可写变换规则: " + unknown);
        return physical;
    }

    private Map<String, String> identityFilterFields(CatalogSnapshot snapshot, CatalogSnapshot.Binding binding) {
        Map<String, String> fields = new LinkedHashMap<>();
        snapshot.rules(binding.id()).stream().filter(CatalogSnapshot.TransformRule::enabled)
                .filter(rule -> rule.mode().readable() && "identity".equals(rule.readTransformerCode()))
                .filter(rule -> rule.physicalPorts().size() == 1 && rule.virtualPorts().size() == 1)
                .forEach(rule -> {
                    CatalogSnapshot.VirtualField field = snapshot.fieldsById().get(rule.virtualPorts().get(0).virtualFieldId());
                    if (field != null) fields.put(field.code(), rule.physicalPorts().get(0).physicalColumnName());
                });
        return fields;
    }

    private VirtualCommandResponse execute(
            List<CommandTask> tasks,
            VirtualCommandRequest request,
            String requestId,
            String planId
    ) {
        VirtualCommandResponse response = new VirtualCommandResponse();
        response.setRequestId(requestId);
        response.setPlanId(planId);
        response.setTransactionMode(request.getTransactionMode().name());
        int affected = 0;
        boolean failed = false;
        int succeededTasks = 0;
        int failedTasks = 0;
        for (CommandTask task : tasks) {
            VirtualCommandResponse.TaskResult item = new VirtualCommandResponse.TaskResult();
            item.setTaskId(task.taskId());
            item.setBindingCode(task.binding().code());
            try {
                PhysicalCommandResult result = commandPort.execute(task.command());
                item.setSuccess(true);
                item.setAffectedRows(result.affectedRows());
                affected += result.affectedRows();
                succeededTasks++;
            } catch (RuntimeException ex) {
                failed = true;
                failedTasks++;
                item.setSuccess(false);
                item.setErrorCode("PHYSICAL_TASK_FAILED");
                item.setErrorMessage("物理写任务执行失败");
                log.warn("virtual data physical command failed: planId={}, taskId={}, bindingCode={}",
                        planId, task.taskId(), task.binding().code(), ex);
                if (request.getTransactionMode() != TransactionMode.BEST_EFFORT) {
                    throw new VirtualDataException("PHYSICAL_TASK_FAILED", "物理写任务执行失败: " + task.taskId(), ex);
                }
            }
            response.getTasks().add(item);
        }
        response.setAffectedRows(affected);
        response.setSuccess(!failed);
        response.setSuccessfulTaskCount(succeededTasks);
        response.setFailedTaskCount(failedTasks);
        response.setPartialSuccess(failed && succeededTasks > 0);
        return response;
    }

    private void validate(VirtualCommandRequest request) {
        if (request == null || request.getEntityCode() == null || request.getCommandType() == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "entityCode 和 commandType 不能为空");
        }
        if (request.getTransactionMode() == null) request.setTransactionMode(TransactionMode.LOCAL);
        if (request.getIdempotencyKey() != null && request.getIdempotencyKey().isBlank()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "idempotencyKey 不能为空白字符串");
        }
        if (request.getCommandType() != CommandType.DELETE && (request.getRecords() == null || request.getRecords().isEmpty())) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "写入记录不能为空");
        }
        if (request.getCommandType() == CommandType.UPDATE && request.getRecords().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "UPDATE 每次只允许一个字段集合");
        }
        if (request.getRecords() != null && request.getRecords().stream().anyMatch(java.util.Objects::isNull)) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "写入记录不能为 null");
        }
    }

    private void validateInsertRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "INSERT 没有可写物理字段");
        }
        Set<String> columns = rows.get(0).keySet();
        if (rows.stream().anyMatch(row -> !row.keySet().equals(columns))) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "同一批 INSERT 的字段集合必须一致");
        }
    }

    private record CommandTask(String taskId, CatalogSnapshot.Binding binding, PhysicalCommand command) {
    }
}
