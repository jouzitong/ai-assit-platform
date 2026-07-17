package ai.platform.aiassit.chat.workflow.data.service.control.impl;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolVersionEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ToolControlDTOs;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.workflow.data.enums.AiChatToolSyncStatus;
import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.workflow.data.enums.ToolAdapterType;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatToolMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatToolVersionMapper;
import ai.platform.aiassit.chat.workflow.data.service.control.AiToolControlService;
import ai.platform.aiassit.chat.workflow.data.support.ControlPlaneJsonSupport;
import ai.platform.aiassit.chat.workflow.data.validator.ToolDefinitionValidator;
import ai.platform.aiassit.service.ai.spi.agent.AgentTemporaryTokenIssuer;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionRequest;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionResult;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutor;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiToolControlServiceImpl implements AiToolControlService {

    private final AiChatToolMapper toolMapper;
    private final AiChatToolVersionMapper versionMapper;
    private final ToolDefinitionValidator validator;
    private final ControlPlaneJsonSupport json;
    private final ObjectProvider<ManagedToolExecutor> managedToolExecutor;
    private final ObjectProvider<AgentTemporaryTokenIssuer> temporaryTokenIssuer;

    public AiToolControlServiceImpl(AiChatToolMapper toolMapper,
                                    AiChatToolVersionMapper versionMapper,
                                    ToolDefinitionValidator validator,
                                    ControlPlaneJsonSupport json,
                                    ObjectProvider<ManagedToolExecutor> managedToolExecutor,
                                    ObjectProvider<AgentTemporaryTokenIssuer> temporaryTokenIssuer) {
        this.toolMapper = toolMapper;
        this.versionMapper = versionMapper;
        this.validator = validator;
        this.json = json;
        this.managedToolExecutor = managedToolExecutor;
        this.temporaryTokenIssuer = temporaryTokenIssuer;
    }

    @Override
    public List<ToolControlDTOs.Catalog> listCatalogs() {
        return toolMapper.selectList(Wrappers.<AiChatToolEntity>lambdaQuery()
                        .orderByDesc(AiChatToolEntity::getUpdateTime)
                        .orderByDesc(AiChatToolEntity::getId))
                .stream().map(this::toCatalogDTO).toList();
    }

    @Override
    public ToolControlDTOs.Version getTool(String toolCode) {
        String code = normalizeCode(toolCode);
        AiChatToolEntity catalog = requireCatalog(code);
        AiChatToolVersionEntity version = latestMutable(code);
        if (version == null) version = latestPublished(code);
        if (version == null) version = latest(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolControlDTOs.Version createDraft(ToolControlDTOs.DraftRequest request) {
        requireRequest(request);
        String code = normalizeCode(request.getCode());
        if (findCatalog(code) != null) throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        AiChatToolEntity catalog = new AiChatToolEntity();
        catalog.setCode(code);
        catalog.setName(request.getName().trim());
        catalog.setDesc(trimToNull(request.getDescription()));
        catalog.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        catalog.setSyncStatus(AiChatToolSyncStatus.PENDING);
        catalog.setRuntimeType(primaryBindingType(request));
        toolMapper.insert(catalog);
        AiChatToolVersionEntity version = persistVersion(catalog, request);
        log.info("Tool created: toolCode={}, version={}, result=success", code, version.getVersionNo());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolControlDTOs.Version createVersion(String toolCode, ToolControlDTOs.DraftRequest request) {
        String code = normalizeCode(toolCode);
        AiChatToolEntity catalog = requireCatalog(code);
        alignPath(request, code, catalog.getName());
        AiChatToolVersionEntity version = persistVersion(catalog, request);
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolControlDTOs.Version updateTool(String toolCode, ToolControlDTOs.DraftRequest request) {
        String code = normalizeCode(toolCode);
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatToolEntity catalog = requireCatalog(code);
        if (StringUtils.hasText(request.getName())) catalog.setName(request.getName().trim());
        if (request.getDescription() != null) catalog.setDesc(trimToNull(request.getDescription()));
        if (request.getEnabled() != null) catalog.setEnabled(request.getEnabled());
        if (hasDefinition(request)) {
            catalog.setRuntimeType(primaryBindingType(request));
            catalog.setSyncStatus(AiChatToolSyncStatus.PENDING);
        }
        toolMapper.updateById(catalog);
        AiChatToolVersionEntity version = latestMutable(code);
        if (hasDefinition(request)) {
            alignPath(request, code, catalog.getName());
            if (version == null) {
                version = persistVersion(catalog, request);
            } else {
                Map<String, Object> definition = definitionOf(request);
                String content = json.write(definition);
                version.setAdapterType(resolveAdapterType(request));
                version.setDefinitionJson(content);
                version.setChecksum(json.sha256(content));
                version.setValidationJson(null);
                version.setStatus(DefinitionStatus.DRAFT);
                versionMapper.updateById(version);
            }
        }
        if (version == null) version = latestPublished(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTool(String toolCode) {
        String code = normalizeCode(toolCode);
        AiChatToolEntity catalog = requireCatalog(code);
        long published = versionMapper.selectCount(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code)
                .eq(AiChatToolVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
        if (published > 0) throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        versionMapper.delete(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code));
        boolean deleted = toolMapper.deleteById(catalog.getId()) > 0;
        log.info("Tool deleted: toolCode={}, result={}", code, deleted ? "success" : "not_changed");
        return deleted;
    }

    @Override
    public List<ToolControlDTOs.Version> listVersions(String toolCode) {
        String code = normalizeCode(toolCode);
        AiChatToolEntity catalog = requireCatalog(code);
        return versionMapper.selectList(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                        .eq(AiChatToolVersionEntity::getToolCode, code)
                        .orderByDesc(AiChatToolVersionEntity::getVersionNo))
                .stream().map(item -> toDTO(item, catalog)).toList();
    }

    @Override
    public ToolControlDTOs.Version getVersion(String toolCode, Integer version) {
        String code = normalizeCode(toolCode);
        return toDTO(requireVersion(code, version), requireCatalog(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ValidationReportDTO validateVersion(String toolCode, Integer version) {
        AiChatToolVersionEntity entity = requireVersion(normalizeCode(toolCode), version);
        Map<String, Object> definition = json.readMap(entity.getDefinitionJson());
        ValidationReportDTO report = validateDefinition(entity.getAdapterType(), definition);
        entity.setValidationJson(json.write(report));
        if (entity.getStatus() != DefinitionStatus.PUBLISHED) {
            entity.setStatus(report.isValid() ? DefinitionStatus.VALIDATED : DefinitionStatus.DRAFT);
        }
        versionMapper.updateById(entity);
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolControlDTOs.Version publishVersion(String toolCode, Integer version) {
        String code = normalizeCode(toolCode);
        AiChatToolVersionEntity entity = requireVersion(code, version);
        AiChatToolEntity catalog = requireCatalog(code);
        if (entity.getStatus() == DefinitionStatus.PUBLISHED) return toDTO(entity, catalog);
        Map<String, Object> definition = json.readMap(entity.getDefinitionJson());
        ValidationReportDTO report = validateDefinition(entity.getAdapterType(), definition);
        if (!report.isValid()) {
            entity.setValidationJson(json.write(report));
            entity.setStatus(DefinitionStatus.DRAFT);
            versionMapper.updateById(entity);
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        entity.setValidationJson(json.write(report));
        entity.setStatus(DefinitionStatus.PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(entity);
        catalog.setSyncStatus(AiChatToolSyncStatus.SUCCESS);
        catalog.setEnabled(Boolean.TRUE);
        catalog.setRemark("currentPublishedVersion=" + version);
        toolMapper.updateById(catalog);
        log.info("Tool published: toolCode={}, version={}, adapterType={}, result=success",
                code, version, entity.getAdapterType());
        return toDTO(entity, catalog);
    }

    @Override
    public Map<String, Object> testVersion(String toolCode, Integer version, Map<String, Object> payload) {
        AiChatToolVersionEntity entity = requireVersion(normalizeCode(toolCode), version);
        Map<String, Object> definition = json.readMap(entity.getDefinitionJson());
        ValidationReportDTO report = validateDefinition(entity.getAdapterType(), definition);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolCode", entity.getToolCode());
        result.put("version", entity.getVersionNo());
        result.put("valid", report.isValid());
        result.put("validation", report);
        result.put("input", payload == null ? Map.of() : payload);
        if (!report.isValid() || !isManagedCode(definition)) {
            result.put("mode", "BINDING_VALIDATION_DRY_RUN");
            return result;
        }
        ManagedToolExecutor executor = managedToolExecutor.getIfAvailable();
        if (executor == null) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Managed Tool runtime is not available");
        }
        String token = null;
        Object current = SystemContext.getUserContext();
        AgentTemporaryTokenIssuer issuer = temporaryTokenIssuer.getIfAvailable();
        if (current instanceof UserContext userContext && issuer != null) {
            token = issuer.issue(userContext);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("toolCode", entity.getToolCode());
        context.put("toolVersion", entity.getVersionNo());
        context.put("testRun", true);
        context.put("config", map(definition.get("runtimeConfig")));
        ManagedToolExecutionResult execution = executor.execute(ManagedToolExecutionRequest.builder()
                .definition(definition)
                .arguments(payload == null ? Map.of() : payload)
                .context(context)
                .executionToken(token)
                .build());
        result.put("mode", "MANAGED_CODE");
        result.put("output", execution.getOutput());
        result.put("stdout", execution.getStdout());
        result.put("stderr", execution.getStderr());
        result.put("durationMs", execution.getDurationMs());
        return result;
    }

    private AiChatToolVersionEntity persistVersion(AiChatToolEntity catalog,
                                                    ToolControlDTOs.DraftRequest request) {
        Map<String, Object> definition = definitionOf(request);
        String content = json.write(definition);
        AiChatToolVersionEntity version = new AiChatToolVersionEntity();
        version.setToolCode(catalog.getCode());
        version.setVersionNo(nextVersion(catalog.getCode()));
        version.setStatus(DefinitionStatus.DRAFT);
        version.setAdapterType(resolveAdapterType(request));
        version.setDefinitionJson(content);
        version.setChecksum(json.sha256(content));
        versionMapper.insert(version);
        return version;
    }

    private Map<String, Object> definitionOf(ToolControlDTOs.DraftRequest request) {
        boolean noBindings = request.getBindings() == null || request.getBindings().isEmpty();
        boolean noInputSchema = request.getInputSchema() == null || request.getInputSchema().isEmpty();
        boolean noOutputSchema = request.getOutputSchema() == null || request.getOutputSchema().isEmpty();
        boolean noManagedSource = !StringUtils.hasText(request.getSourceCode());
        if (request.getDefinition() != null && noBindings && noInputSchema && noOutputSchema && noManagedSource) {
            return new LinkedHashMap<>(request.getDefinition());
        }
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("inputSchema", request.getInputSchema() == null ? Map.of() : request.getInputSchema());
        definition.put("outputSchema", request.getOutputSchema() == null ? Map.of() : request.getOutputSchema());
        definition.put("permissionPolicy", request.getPermissionPolicy() == null ? Map.of() : request.getPermissionPolicy());
        definition.put("approvalPolicy", request.getApprovalPolicy() == null ? Map.of() : request.getApprovalPolicy());
        definition.put("timeoutMs", request.getTimeoutMs() == null ? 30_000 : request.getTimeoutMs());
        definition.put("executionMode", isManagedCodeRequest(request)
                ? "MANAGED_CODE" : "PORTABLE_BINDING");
        definition.put("implementationRuntime", StringUtils.hasText(request.getImplementationRuntime())
                ? request.getImplementationRuntime().trim().toUpperCase() : "PYTHON");
        definition.put("compatibleAgentRuntimes", request.getCompatibleAgentRuntimes() == null
                || request.getCompatibleAgentRuntimes().isEmpty()
                ? List.of("OPENAI_AGENTS_PYTHON", "OPENAI_AGENTS_TYPESCRIPT")
                : request.getCompatibleAgentRuntimes());
        definition.put("sourceCode", request.getSourceCode());
        definition.put("runtimeConfig", request.getRuntimeConfig() == null ? Map.of() : request.getRuntimeConfig());
        definition.put("bindings", request.getBindings() == null ? List.of() : request.getBindings());
        return definition;
    }

    private ToolControlDTOs.Version toDTO(AiChatToolVersionEntity entity, AiChatToolEntity catalog) {
        Map<String, Object> definition = json.readMap(entity.getDefinitionJson());
        ToolControlDTOs.Version dto = new ToolControlDTOs.Version();
        dto.setId(entity.getId());
        dto.setToolCode(entity.getToolCode());
        dto.setCode(entity.getToolCode());
        dto.setName(catalog.getName());
        dto.setDescription(catalog.getDesc());
        dto.setEnabled(catalog.getEnabled());
        dto.setVersion(entity.getVersionNo());
        AiChatToolVersionEntity published = latestPublished(entity.getToolCode());
        AiChatToolVersionEntity draft = latestMutable(entity.getToolCode());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setAdapterType(entity.getAdapterType() == null ? null : entity.getAdapterType().name());
        dto.setDefinition(definition);
        dto.setInputSchema(map(definition.get("inputSchema")));
        dto.setOutputSchema(map(definition.get("outputSchema")));
        dto.setPermissionPolicy(map(definition.get("permissionPolicy")));
        dto.setApprovalPolicy(map(definition.get("approvalPolicy")));
        dto.setTimeoutMs(definition.get("timeoutMs") instanceof Number number ? number.intValue() : null);
        dto.setExecutionMode(text(definition.get("executionMode")));
        dto.setImplementationRuntime(text(definition.get("implementationRuntime")));
        dto.setCompatibleAgentRuntimes(strings(definition.get("compatibleAgentRuntimes")));
        dto.setSourceCode(text(definition.get("sourceCode")));
        dto.setRuntimeConfig(map(definition.get("runtimeConfig")));
        dto.setBindings(bindings(definition.get("bindings")));
        dto.setValidation(json.read(entity.getValidationJson(), ValidationReportDTO.class));
        dto.setChecksum(entity.getChecksum());
        dto.setPublishedAt(entity.getPublishedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private ToolControlDTOs.Catalog toCatalogDTO(AiChatToolEntity entity) {
        ToolControlDTOs.Catalog dto = new ToolControlDTOs.Catalog();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDesc());
        dto.setEnabled(entity.getEnabled());
        AiChatToolVersionEntity published = latestPublished(entity.getCode());
        AiChatToolVersionEntity draft = latestMutable(entity.getCode());
        AiChatToolVersionEntity current = draft != null ? draft : published;
        dto.setStatus(current == null || current.getStatus() == null ? null : current.getStatus().name());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private ToolAdapterType resolveAdapterType(ToolControlDTOs.DraftRequest request) {
        if (isManagedCodeRequest(request)) {
            return ToolAdapterType.FUNCTION;
        }
        if (request.getAdapterType() != null) return request.getAdapterType();
        String type = primaryBindingType(request);
        return switch (type) {
            case "HTTP" -> ToolAdapterType.HTTP;
            case "MCP" -> ToolAdapterType.MCP;
            case "PYTHON_MODULE", "JAVASCRIPT_MODULE" -> ToolAdapterType.SCRIPT;
            default -> ToolAdapterType.FUNCTION;
        };
    }

    private String primaryBindingType(ToolControlDTOs.DraftRequest request) {
        if (isManagedCodeRequest(request)) {
            return StringUtils.hasText(request.getImplementationRuntime())
                    ? request.getImplementationRuntime().trim().toUpperCase() : "PYTHON";
        }
        if (request != null && request.getBindings() != null) {
            for (ToolControlDTOs.Binding binding : request.getBindings()) {
                if (binding != null && Boolean.TRUE.equals(binding.getEnabled())
                        && StringUtils.hasText(binding.getBindingType())) {
                    return binding.getBindingType().trim().toUpperCase();
                }
            }
        }
        return request != null && request.getAdapterType() != null ? request.getAdapterType().name() : "UNKNOWN";
    }

    private boolean hasDefinition(ToolControlDTOs.DraftRequest request) {
        return request.getDefinition() != null || (request.getBindings() != null && !request.getBindings().isEmpty())
                || StringUtils.hasText(request.getSourceCode())
                || (request.getInputSchema() != null && !request.getInputSchema().isEmpty());
    }

    private boolean isManagedCodeRequest(ToolControlDTOs.DraftRequest request) {
        return request != null && ("MANAGED_CODE".equalsIgnoreCase(request.getExecutionMode())
                || (!StringUtils.hasText(request.getExecutionMode()) && StringUtils.hasText(request.getSourceCode())));
    }

    private ValidationReportDTO validateDefinition(ToolAdapterType adapterType, Map<String, Object> definition) {
        ValidationReportDTO report = validator.validate(adapterType, definition);
        if (report.isValid() && isManagedCode(definition)) {
            ManagedToolExecutor executor = managedToolExecutor.getIfAvailable();
            if (executor == null) {
                report.error("Managed Tool runtime is not available");
            } else {
                executor.validate(definition).forEach(report::error);
            }
            report.finish();
        }
        return report;
    }

    private boolean isManagedCode(Map<String, Object> definition) {
        return "MANAGED_CODE".equalsIgnoreCase(text(definition.get("executionMode")));
    }

    private void requireRequest(ToolControlDTOs.DraftRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode()) || !StringUtils.hasText(request.getName())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private void alignPath(ToolControlDTOs.DraftRequest request, String code, String name) {
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        request.setCode(code);
        if (!StringUtils.hasText(request.getName())) request.setName(name);
    }

    private AiChatToolVersionEntity requireVersion(String code, Integer version) {
        if (version == null || version < 1) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatToolVersionEntity entity = versionMapper.selectOne(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code)
                .eq(AiChatToolVersionEntity::getVersionNo, version));
        if (entity == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return entity;
    }

    private AiChatToolEntity requireCatalog(String code) {
        AiChatToolEntity entity = findCatalog(code);
        if (entity == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return entity;
    }

    private AiChatToolEntity findCatalog(String code) {
        return toolMapper.selectOne(Wrappers.<AiChatToolEntity>lambdaQuery()
                .eq(AiChatToolEntity::getCode, code));
    }

    private AiChatToolVersionEntity latest(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code)
                .orderByDesc(AiChatToolVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatToolVersionEntity latestMutable(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code)
                .ne(AiChatToolVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .ne(AiChatToolVersionEntity::getStatus, DefinitionStatus.ARCHIVED)
                .orderByDesc(AiChatToolVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatToolVersionEntity latestPublished(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code)
                .eq(AiChatToolVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .orderByDesc(AiChatToolVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private int nextVersion(String code) {
        AiChatToolVersionEntity latest = latest(code);
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : new LinkedHashMap<>();
    }

    private List<ToolControlDTOs.Binding> bindings(Object value) {
        if (!(value instanceof List<?> values)) return new ArrayList<>();
        List<ToolControlDTOs.Binding> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                result.add(json.read(json.write(map), ToolControlDTOs.Binding.class));
            }
        }
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9._-]{1,255}")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
