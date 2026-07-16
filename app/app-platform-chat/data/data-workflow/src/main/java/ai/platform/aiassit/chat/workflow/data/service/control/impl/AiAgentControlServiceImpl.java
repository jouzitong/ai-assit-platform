package ai.platform.aiassit.chat.workflow.data.service.control.impl;

import ai.platform.aiassit.chat.workflow.data.entity.AiAgentEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiAgentEntryBindingEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiAgentVersionEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillFileEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillVersionEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolVersionEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowVersionEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.workflow.data.mapper.AiAgentEntryBindingMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiAgentMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiAgentVersionMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillFileMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillVersionMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatToolVersionMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowVersionMapper;
import ai.platform.aiassit.chat.workflow.data.service.control.AiAgentControlService;
import ai.platform.aiassit.chat.workflow.data.support.ControlPlaneJsonSupport;
import ai.platform.aiassit.chat.workflow.data.support.ControlPlaneReferenceParser;
import ai.platform.aiassit.chat.workflow.data.validator.AgentGraphValidator;
import ai.platform.aiassit.chat.workflow.data.validator.AgentManifestValidator;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiAgentControlServiceImpl implements AiAgentControlService, AgentDefinitionStore {

    private static final long MAX_INLINE_SKILL_FILE_BYTES = 256L * 1024;
    private static final long MAX_INLINE_SKILL_SNAPSHOT_BYTES = 2L * 1024 * 1024;
    private static final Set<String> SENSITIVE_CONFIG_KEYS = Set.of(
            "apikey", "api_key", "token", "secret", "password", "credential", "cookie");

    private final AiAgentMapper agentMapper;
    private final AiAgentVersionMapper versionMapper;
    private final AiAgentEntryBindingMapper bindingMapper;
    private final AiChatSkillVersionMapper skillVersionMapper;
    private final AiChatSkillFileMapper skillFileMapper;
    private final AiChatToolVersionMapper toolVersionMapper;
    private final AiChatWorkflowVersionMapper workflowVersionMapper;
    private final AgentGraphValidator graphValidator;
    private final AgentManifestValidator validator;
    private final ControlPlaneJsonSupport json;
    private final ControlPlaneReferenceParser references;

    public AiAgentControlServiceImpl(AiAgentMapper agentMapper,
                                     AiAgentVersionMapper versionMapper,
                                     AiAgentEntryBindingMapper bindingMapper,
                                     AiChatSkillVersionMapper skillVersionMapper,
                                     AiChatSkillFileMapper skillFileMapper,
                                     AiChatToolVersionMapper toolVersionMapper,
                                     AiChatWorkflowVersionMapper workflowVersionMapper,
                                     AgentGraphValidator graphValidator,
                                     AgentManifestValidator validator,
                                     ControlPlaneJsonSupport json,
                                     ControlPlaneReferenceParser references) {
        this.agentMapper = agentMapper;
        this.versionMapper = versionMapper;
        this.bindingMapper = bindingMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillFileMapper = skillFileMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.graphValidator = graphValidator;
        this.validator = validator;
        this.json = json;
        this.references = references;
    }

    @Override
    public List<AgentControlDTOs.Catalog> listAgents() {
        return agentMapper.selectList(Wrappers.<AiAgentEntity>lambdaQuery()
                        .orderByDesc(AiAgentEntity::getUpdateTime)
                        .orderByDesc(AiAgentEntity::getId))
                .stream().map(this::toCatalogDTO).toList();
    }

    @Override
    public AgentControlDTOs.Version getAgent(String agentCode) {
        String code = normalizeCode(agentCode);
        AiAgentEntity agent = requireAgent(code);
        AiAgentVersionEntity version = latestEditableOrPublishedVersion(agent);
        if (version == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return toVersionDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.Version createAgent(AgentControlDTOs.CreateRequest request) {
        requireCreateRequest(request);
        String code = normalizeCode(request.getCode());
        if (findAgent(code) != null) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
        AiAgentEntity agent = new AiAgentEntity();
        agent.setCode(code);
        agent.setName(request.getName().trim());
        agent.setDescription(trimToNull(request.getDescription()));
        agent.setStatus(DefinitionStatus.DRAFT);
        agent.setEnabled(Boolean.TRUE);
        agentMapper.insert(agent);

        AiAgentVersionEntity version = persistDraft(agent, manifestOf(request));
        log.info("Agent created: agentCode={}, version={}, result=success", code, version.getVersionNo());
        return toVersionDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.Version updateAgent(String agentCode, AgentControlDTOs.UpdateRequest request) {
        String code = normalizeCode(agentCode);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        AiAgentEntity agent = requireAgent(code);
        agent.setName(request.getName().trim());
        agent.setDescription(trimToNull(request.getDescription()));
        if (request.getEnabled() != null) {
            agent.setEnabled(request.getEnabled());
        }
        agentMapper.updateById(agent);
        AgentControlDTOs.Manifest manifest = manifestOf(request);
        AiAgentVersionEntity draft = latestMutableVersion(code);
        if (manifest != null) {
            if (draft == null) {
                draft = persistDraft(agent, manifest);
            } else {
                enrichManifestIdentity(agent, draft.getVersionNo(), manifest);
                String manifestJson = json.write(manifest);
                draft.setManifestJson(manifestJson);
                draft.setChecksum(json.sha256(manifestJson));
                draft.setValidationJson(null);
                draft.setStatus(DefinitionStatus.DRAFT);
                versionMapper.updateById(draft);
            }
        }
        if (draft == null) {
            draft = latestEditableOrPublishedVersion(agent);
        }
        log.info("Agent catalog updated: agentCode={}, enabled={}, result=success", code, agent.getEnabled());
        return toVersionDTO(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAgent(String agentCode) {
        String code = normalizeCode(agentCode);
        AiAgentEntity agent = requireAgent(code);
        long activeBindings = bindingMapper.selectCount(Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                .eq(AiAgentEntryBindingEntity::getAgentCode, code)
                .eq(AiAgentEntryBindingEntity::getEnabled, Boolean.TRUE));
        if (activeBindings > 0) {
            log.warn("Agent delete rejected: agentCode={}, activeBindingCount={}", code, activeBindings);
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
        versionMapper.delete(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                .eq(AiAgentVersionEntity::getAgentCode, code));
        boolean deleted = agentMapper.deleteById(agent.getId()) > 0;
        log.info("Agent deleted: agentCode={}, result={}", code, deleted ? "success" : "not_changed");
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.Version createVersion(String agentCode,
                                                  AgentControlDTOs.VersionCreateRequest request) {
        String code = normalizeCode(agentCode);
        if (request == null || manifestOf(request) == null) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        AiAgentEntity agent = requireAgent(code);
        AiAgentVersionEntity version = persistDraft(agent, manifestOf(request));
        log.info("Agent version created: agentCode={}, version={}, result=success", code, version.getVersionNo());
        return toVersionDTO(version);
    }

    @Override
    public List<AgentControlDTOs.Version> listVersions(String agentCode) {
        String code = normalizeCode(agentCode);
        requireAgent(code);
        return versionMapper.selectList(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                        .eq(AiAgentVersionEntity::getAgentCode, code)
                        .orderByDesc(AiAgentVersionEntity::getVersionNo))
                .stream().map(this::toVersionDTO).toList();
    }

    @Override
    public AgentControlDTOs.Version getVersion(String agentCode, Integer version) {
        return toVersionDTO(requireVersion(normalizeCode(agentCode), version));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ValidationReportDTO validateVersion(String agentCode, Integer version) {
        String code = normalizeCode(agentCode);
        AiAgentVersionEntity entity = requireVersion(code, version);
        AgentControlDTOs.Manifest manifest = json.read(entity.getManifestJson(), AgentControlDTOs.Manifest.class);
        ValidationReportDTO report = validateWithReferences(code, manifest, false);
        entity.setValidationJson(json.write(report));
        if (entity.getStatus() != DefinitionStatus.PUBLISHED) {
            entity.setStatus(report.isValid() ? DefinitionStatus.VALIDATED : DefinitionStatus.DRAFT);
        }
        versionMapper.updateById(entity);
        AiAgentEntity agent = requireAgent(code);
        if (agent.getCurrentVersion() == null) {
            agent.setStatus(entity.getStatus());
            agentMapper.updateById(agent);
        }
        log.info("Agent version validated: agentCode={}, version={}, valid={}, errorCount={}",
                code, version, report.isValid(), report.getErrors().size());
        return report;
    }

    @Override
    public ValidationReportDTO compatibility(String agentCode, Integer version) {
        String code = normalizeCode(agentCode);
        AiAgentVersionEntity entity = requireVersion(code, version);
        AgentControlDTOs.Manifest manifest = json.read(entity.getManifestJson(), AgentControlDTOs.Manifest.class);
        ValidationReportDTO report = validateWithReferences(code, manifest, false);
        validatePortableCapabilities(manifest == null ? null : manifest.getSpec(), report);
        report.finish();
        if (report.isCompatible()) {
            report.setMessage("portable Agent contract is compatible with OpenAI Agents Python and TypeScript runtimes");
        }
        return report;
    }

    @Override
    public Map<String, Object> testVersion(String agentCode,
                                           Integer version,
                                           Map<String, Object> input) {
        String code = normalizeCode(agentCode);
        AiAgentEntity agent = requireAgent(code);
        AiAgentVersionEntity entity = requireVersion(code, version);
        AgentControlDTOs.Manifest manifest = json.read(entity.getManifestJson(), AgentControlDTOs.Manifest.class);
        ValidationReportDTO validation = validateWithReferences(code, manifest, false);
        ValidationReportDTO compatibility = compatibility(code, version);
        boolean success = validation.isValid() && compatibility.isCompatible();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("mode", "DRY_RUN");
        result.put("agentCode", code);
        result.put("agentVersion", version);
        result.put("input", input == null ? Map.of() : new LinkedHashMap<>(input));
        result.put("validation", validation);
        result.put("compatibility", compatibility);
        if (success) {
            StoredAgentDefinition snapshot = toStoredDefinition(agent, entity, firstEnabledBinding(code, version));
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("manifest", json.readMap(snapshot.getManifestJson()));
            preview.put("resolvedCapabilities", json.readMap(snapshot.getResolvedCapabilitiesJson()));
            preview.put("workflow", json.readMap(snapshot.getWorkflowSnapshotJson()));
            preview.put("runtimeType", snapshot.getRuntimeType());
            preview.put("sdkVersion", snapshot.getSdkVersion());
            preview.put("checksum", snapshot.getChecksum());
            result.put("snapshotPreview", preview);
        }
        result.put("message", success
                ? "Agent definition and frozen references passed the portable runtime dry-run"
                : "Agent dry-run failed validation or compatibility checks");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.Version publishVersion(String agentCode, Integer version) {
        String code = normalizeCode(agentCode);
        AiAgentEntity agent = requireAgent(code);
        AiAgentVersionEntity entity = requireVersion(code, version);
        if (entity.getStatus() == DefinitionStatus.PUBLISHED) {
            return toVersionDTO(entity);
        }
        AgentControlDTOs.Manifest manifest = json.read(entity.getManifestJson(), AgentControlDTOs.Manifest.class);
        ValidationReportDTO report = validateWithReferences(code, manifest, true);
        if (!report.isValid()) {
            entity.setValidationJson(json.write(report));
            entity.setStatus(DefinitionStatus.DRAFT);
            versionMapper.updateById(entity);
            log.warn("Agent publish rejected: agentCode={}, version={}, errors={}",
                    code, version, report.getErrors().size());
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        enrichManifestIdentity(agent, entity.getVersionNo(), manifest);
        String frozenManifest = json.write(manifest);
        entity.setManifestJson(frozenManifest);
        entity.setChecksum(json.sha256(frozenManifest));
        entity.setValidationJson(json.write(report));
        entity.setStatus(DefinitionStatus.PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(entity);

        agent.setCurrentVersion(entity.getVersionNo());
        agent.setStatus(DefinitionStatus.PUBLISHED);
        agent.setEnabled(Boolean.TRUE);
        agentMapper.updateById(agent);
        log.info("Agent version published: agentCode={}, version={}, checksum={}, result=success",
                code, version, entity.getChecksum());
        return toVersionDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.EntryBinding upsertEntryBinding(
            String entryCode,
            AgentControlDTOs.EntryBindingRequest request) {
        String normalizedEntry = normalizeEntryCode(entryCode);
        if (request == null || request.getRuntimeType() == null || request.getAgentVersion() == null) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        String agentCode = normalizeCode(request.getAgentCode());
        requirePublishedVersion(agentCode, request.getAgentVersion());
        Map<String, Object> config = request.getConfig() == null ? Map.of() : request.getConfig();
        if (containsSensitiveKey(config)) {
            log.warn("Agent entry binding rejected because config contains a credential-like key: entryCode={}, agentCode={}",
                    normalizedEntry, agentCode);
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        AiAgentEntryBindingEntity binding = bindingMapper.selectOne(
                Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                        .eq(AiAgentEntryBindingEntity::getEntryCode, normalizedEntry)
                        .eq(AiAgentEntryBindingEntity::getAgentCode, agentCode)
                        .eq(AiAgentEntryBindingEntity::getRuntimeType, request.getRuntimeType()));
        boolean creating = binding == null;
        if (creating) {
            binding = new AiAgentEntryBindingEntity();
            binding.setEntryCode(normalizedEntry);
            binding.setAgentCode(agentCode);
            binding.setRuntimeType(request.getRuntimeType());
        }
        binding.setAgentVersion(request.getAgentVersion());
        binding.setSdkVersion(trimToNull(request.getSdkVersion()));
        binding.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        binding.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        binding.setConfigJson(json.write(config));
        if (creating) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
        log.info("Agent entry binding upserted: entryCode={}, agentCode={}, agentVersion={}, runtimeType={}, enabled={}, result=success",
                normalizedEntry, agentCode, request.getAgentVersion(), request.getRuntimeType(), binding.getEnabled());
        return toBindingDTO(binding);
    }

    @Override
    public List<AgentControlDTOs.EntryBinding> listEntryBindings(String entryCode) {
        String normalizedEntry = normalizeEntryCode(entryCode);
        return bindingMapper.selectList(Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                        .eq(AiAgentEntryBindingEntity::getEntryCode, normalizedEntry)
                        .orderByAsc(AiAgentEntryBindingEntity::getPriority)
                        .orderByAsc(AiAgentEntryBindingEntity::getId))
                .stream().map(this::toBindingDTO).toList();
    }

    @Override
    public List<AgentControlDTOs.EntrySelection> listEntrySelections() {
        List<AiAgentEntryBindingEntity> bindings = bindingMapper.selectList(
                Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                        .orderByAsc(AiAgentEntryBindingEntity::getEntryCode)
                        .orderByAsc(AiAgentEntryBindingEntity::getPriority)
                        .orderByAsc(AiAgentEntryBindingEntity::getId));
        Map<String, AgentControlDTOs.EntrySelection> selections = new LinkedHashMap<>();
        for (AiAgentEntryBindingEntity binding : bindings) {
            selections.putIfAbsent(binding.getEntryCode(), toEntrySelection(binding));
        }
        return new ArrayList<>(selections.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlDTOs.EntrySelection updateEntrySelection(
            String entryCode,
            AgentControlDTOs.EntrySelectionRequest request) {
        String normalizedEntry = normalizeEntryCode(entryCode);
        if (request == null || !StringUtils.hasText(request.getAgentCode())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        String agentCode = normalizeCode(request.getAgentCode());
        AiAgentEntity agent = requireAgent(agentCode);
        String strategy = StringUtils.hasText(request.getVersionStrategy())
                ? request.getVersionStrategy().trim().toUpperCase(Locale.ROOT) : "LATEST_PUBLISHED";
        Integer selectedVersion;
        if ("PINNED".equals(strategy)) {
            selectedVersion = request.getPinnedVersion();
        } else if ("LATEST_PUBLISHED".equals(strategy)) {
            selectedVersion = agent.getCurrentVersion();
        } else {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        requirePublishedVersion(agentCode, selectedVersion);
        List<AiAgentEntryBindingEntity> existing = bindingMapper.selectList(
                Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                        .eq(AiAgentEntryBindingEntity::getEntryCode, normalizedEntry)
                        .orderByAsc(AiAgentEntryBindingEntity::getPriority)
                        .orderByAsc(AiAgentEntryBindingEntity::getId));
        AiAgentEntryBindingEntity binding = existing.stream()
                .filter(item -> item.getAgentCode().equals(agentCode))
                .findFirst().orElse(null);
        if (binding == null) {
            binding = new AiAgentEntryBindingEntity();
            binding.setEntryCode(normalizedEntry);
            binding.setAgentCode(agentCode);
            binding.setRuntimeType(ai.platform.aiassit.chat.workflow.data.enums.AgentRuntimeType.OPENAI_AGENTS_PYTHON);
            binding.setSdkVersion("latest-compatible");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("versionStrategy", strategy);
        if ("PINNED".equals(strategy)) config.put("pinnedVersion", selectedVersion);
        binding.setAgentVersion(selectedVersion);
        binding.setPriority(0);
        binding.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        binding.setConfigJson(json.write(config));
        if (binding.getId() == null) bindingMapper.insert(binding);
        else bindingMapper.updateById(binding);
        for (AiAgentEntryBindingEntity other : existing) {
            if (!other.getId().equals(binding.getId()) && other.getPriority() != null && other.getPriority() <= 0) {
                other.setPriority(100);
                bindingMapper.updateById(other);
            }
        }
        log.info("Agent entry selection updated: entryCode={}, agentCode={}, strategy={}, version={}, enabled={}, result=success",
                normalizedEntry, agentCode, strategy, selectedVersion, binding.getEnabled());
        return toEntrySelection(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEntryBinding(Long bindingId) {
        if (bindingId == null) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        AiAgentEntryBindingEntity binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        boolean deleted = bindingMapper.deleteById(bindingId) > 0;
        log.info("Agent entry binding deleted: bindingId={}, entryCode={}, agentCode={}, result={}",
                bindingId, binding.getEntryCode(), binding.getAgentCode(), deleted ? "success" : "not_changed");
        return deleted;
    }

    /** Runtime lookup; explicit versions are still restricted to PUBLISHED rows. */
    @Override
    public Optional<StoredAgentDefinition> resolve(String agentCode, Integer version) {
        if (!StringUtils.hasText(agentCode)) {
            return Optional.empty();
        }
        String code;
        try {
            code = normalizeCode(agentCode);
        } catch (BizException ex) {
            return Optional.empty();
        }
        AiAgentEntity agent = findAgent(code);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
            return Optional.empty();
        }
        Integer resolvedVersion = version == null ? agent.getCurrentVersion() : version;
        AiAgentVersionEntity published = findPublishedVersion(code, resolvedVersion);
        if (published == null) {
            return Optional.empty();
        }
        AiAgentEntryBindingEntity binding = firstEnabledBinding(code, published.getVersionNo());
        return Optional.of(toStoredDefinition(agent, published, binding));
    }

    @Override
    public Optional<StoredAgentDefinition> resolveEntry(String entryCode) {
        if (!StringUtils.hasText(entryCode)) {
            return Optional.empty();
        }
        List<AiAgentEntryBindingEntity> bindings = enabledEntryBindings(normalizeEntryCode(entryCode));
        for (AiAgentEntryBindingEntity binding : bindings) {
            AiAgentEntity agent = findAgent(binding.getAgentCode());
            AiAgentVersionEntity version = findPublishedVersion(binding.getAgentCode(), binding.getAgentVersion());
            if (agent != null && Boolean.TRUE.equals(agent.getEnabled()) && version != null) {
                return Optional.of(toStoredDefinition(agent, version, binding));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<AgentEntrySummary> listAvailable(String entryCode) {
        if (!StringUtils.hasText(entryCode)) {
            return List.of();
        }
        List<AgentEntrySummary> available = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAgentEntryBindingEntity binding : enabledEntryBindings(normalizeEntryCode(entryCode))) {
            String key = binding.getAgentCode() + ":" + binding.getAgentVersion();
            if (!seen.add(key)) {
                continue;
            }
            AiAgentEntity agent = findAgent(binding.getAgentCode());
            AiAgentVersionEntity version = findPublishedVersion(binding.getAgentCode(), binding.getAgentVersion());
            if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || version == null) {
                continue;
            }
            available.add(AgentEntrySummary.builder()
                    .code(agent.getCode())
                    .name(agent.getName())
                    .description(agent.getDescription())
                    .version(version.getVersionNo())
                    .build());
        }
        return available;
    }

    private AiAgentVersionEntity persistDraft(AiAgentEntity agent, AgentControlDTOs.Manifest manifest) {
        int versionNo = nextVersion(agent.getCode());
        enrichManifestIdentity(agent, versionNo, manifest);
        String manifestJson = json.write(manifest);
        AiAgentVersionEntity version = new AiAgentVersionEntity();
        version.setAgentCode(agent.getCode());
        version.setVersionNo(versionNo);
        version.setStatus(DefinitionStatus.DRAFT);
        version.setManifestJson(manifestJson);
        version.setChecksum(json.sha256(manifestJson));
        versionMapper.insert(version);
        return version;
    }

    private ValidationReportDTO validateWithReferences(String agentCode,
                                                       AgentControlDTOs.Manifest manifest,
                                                       boolean freezeVersions) {
        ValidationReportDTO report = validator.validate(agentCode, manifest);
        if (manifest == null || manifest.getSpec() == null) {
            return report;
        }
        AgentControlDTOs.Spec spec = manifest.getSpec();
        resolveCapabilities(spec.getSkillRefs(), "skill", report, freezeVersions);
        resolveCapabilities(spec.getToolRefs(), "tool", report, freezeVersions);
        validateExternalRefs(spec.getKnowledgeRefs(), "knowledge", report);
        validateExternalRefs(spec.getMcpRefs(), "mcp", report);
        validateCollaborators(spec.getCollaboration(), report, freezeVersions);
        freezeWorkflowRef(spec.getOutput(), report, freezeVersions);
        Integer rootVersion = manifest.getMetadata() == null ? null : manifest.getMetadata().getVersion();
        ValidationReportDTO graphReport = graphValidator.validate(
                agentCode, rootVersion, manifest, this::resolveGraphAgent);
        graphReport.getErrors().forEach(report::error);
        graphReport.getWarnings().forEach(warning -> report.warn(warning.message()));
        report.finish();
        return report;
    }

    private AgentGraphValidator.PublishedAgent resolveGraphAgent(String code, Integer requestedVersion) {
        AiAgentEntity agent = findAgent(code);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) return null;
        Integer version = requestedVersion == null ? agent.getCurrentVersion() : requestedVersion;
        AiAgentVersionEntity published = findPublishedVersion(code, version);
        if (published == null) return null;
        AgentControlDTOs.Manifest manifest = json.read(
                published.getManifestJson(), AgentControlDTOs.Manifest.class);
        return new AgentGraphValidator.PublishedAgent(code, published.getVersionNo(), manifest);
    }

    private void validatePortableCapabilities(AgentControlDTOs.Spec spec, ValidationReportDTO report) {
        if (spec == null) return;
        if (spec.getSkillRefs() != null) {
            for (AgentControlDTOs.CapabilityRef ref : spec.getSkillRefs()) {
                if (ref == null || Boolean.FALSE.equals(ref.getEnabled()) || !StringUtils.hasText(ref.getRef())) {
                    continue;
                }
                try {
                    ControlPlaneReferenceParser.ParsedReference parsed = references.parse(ref.getRef(), "skill");
                    AiChatSkillVersionEntity version = findPublishedSkill(
                            parsed.code(), ref.getVersion() == null ? parsed.version() : ref.getVersion());
                    if (version == null) continue;
                    Object declared = json.readMap(version.getManifestJson()).get("compatibleRuntimes");
                    if (declared instanceof Iterable<?> runtimes) {
                        Set<String> normalized = new HashSet<>();
                        runtimes.forEach(value -> {
                            if (value != null) normalized.add(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
                        });
                        if (!normalized.isEmpty()
                                && (!declaresRuntime(normalized, "PYTHON")
                                || !declaresRuntime(normalized, "TYPESCRIPT"))) {
                            report.error("Skill is not declared compatible with both Python and TypeScript: "
                                    + ref.getRef());
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // The reference validator already emitted the actionable syntax error.
                }
            }
        }
        if (spec.getToolRefs() != null) {
            for (AgentControlDTOs.CapabilityRef ref : spec.getToolRefs()) {
                if (ref == null || Boolean.FALSE.equals(ref.getEnabled()) || !StringUtils.hasText(ref.getRef())) {
                    continue;
                }
                try {
                    ControlPlaneReferenceParser.ParsedReference parsed = references.parse(ref.getRef(), "tool");
                    AiChatToolVersionEntity version = findPublishedTool(
                            parsed.code(), ref.getVersion() == null ? parsed.version() : ref.getVersion());
                    if (version == null) continue;
                    Object bindingsValue = json.readMap(version.getDefinitionJson()).get("bindings");
                    boolean portable = false;
                    if (bindingsValue instanceof Iterable<?> bindings) {
                        for (Object value : bindings) {
                            if (!(value instanceof Map<?, ?> binding) || Boolean.FALSE.equals(binding.get("enabled"))) {
                                continue;
                            }
                            Object bindingType = binding.get("bindingType");
                            String type = String.valueOf(bindingType == null ? "" : bindingType)
                                    .trim().toUpperCase(Locale.ROOT);
                            if ("HTTP".equals(type) || "JAVA_INTERNAL".equals(type)) {
                                portable = true;
                                break;
                            }
                        }
                    }
                    if (!portable) {
                        report.error("Tool has no enabled portable HTTP/JAVA_INTERNAL binding: " + ref.getRef());
                    }
                } catch (IllegalArgumentException ignored) {
                    // The reference validator already emitted the actionable syntax error.
                }
            }
        }
        if (spec.getExtensions() != null) {
            for (String key : spec.getExtensions().keySet()) {
                String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
                if (normalized.contains("python") || normalized.contains("typescript")
                        || normalized.contains("javascript")) {
                    report.error("Runtime-specific Agent extension is not portable: " + key);
                }
            }
        }
    }

    private boolean declaresRuntime(Set<String> values, String expected) {
        if ("PYTHON".equals(expected)) {
            return values.stream().anyMatch(value -> value.contains("PYTHON"));
        }
        return values.stream().anyMatch(value -> value.contains("TYPESCRIPT")
                || value.contains("JAVASCRIPT") || value.endsWith("_JS") || "JS".equals(value));
    }

    private void resolveCapabilities(List<AgentControlDTOs.CapabilityRef> refs,
                                     String type,
                                     ValidationReportDTO report,
                                     boolean freezeVersions) {
        if (refs == null) {
            return;
        }
        for (AgentControlDTOs.CapabilityRef ref : refs) {
            if (ref == null || !StringUtils.hasText(ref.getRef()) || Boolean.FALSE.equals(ref.getEnabled())) {
                continue;
            }
            ControlPlaneReferenceParser.ParsedReference parsed;
            try {
                parsed = references.parse(ref.getRef(), type);
            } catch (IllegalArgumentException ex) {
                report.error(ex.getMessage());
                continue;
            }
            Integer requestedVersion = ref.getVersion() == null ? parsed.version() : ref.getVersion();
            Integer resolvedVersion;
            String checksum;
            if ("skill".equals(type)) {
                AiChatSkillVersionEntity published = findPublishedSkill(parsed.code(), requestedVersion);
                resolvedVersion = published == null ? null : published.getVersionNo();
                checksum = published == null ? null : published.getPackageChecksum();
            } else {
                AiChatToolVersionEntity published = findPublishedTool(parsed.code(), requestedVersion);
                resolvedVersion = published == null ? null : published.getVersionNo();
                checksum = published == null ? null : published.getChecksum();
            }
            if (resolvedVersion == null) {
                report.error(type + "Ref must reference a published version: " + ref.getRef());
            } else if (freezeVersions) {
                ref.setVersion(resolvedVersion);
                ref.setRef(references.freeze(ref.getRef(), type, resolvedVersion));
                ref.setContentHash(checksum);
            }
        }
    }

    private void validateExternalRefs(List<AgentControlDTOs.CapabilityRef> refs,
                                      String type,
                                      ValidationReportDTO report) {
        if (refs == null) return;
        for (AgentControlDTOs.CapabilityRef ref : refs) {
            if (ref == null || Boolean.FALSE.equals(ref.getEnabled())) continue;
            try {
                references.parse(ref.getRef(), type);
            } catch (IllegalArgumentException ex) {
                report.error(ex.getMessage());
            }
        }
    }

    private void validateCollaborators(AgentControlDTOs.Collaboration collaboration,
                                       ValidationReportDTO report,
                                       boolean freezeVersions) {
        if (collaboration == null) return;
        List<AgentControlDTOs.CollaboratorRef> refs = new ArrayList<>();
        if (collaboration.getAgentTools() != null) refs.addAll(collaboration.getAgentTools());
        if (collaboration.getHandoffs() != null) refs.addAll(collaboration.getHandoffs());
        for (AgentControlDTOs.CollaboratorRef collaborator : refs) {
            if (collaborator == null || !StringUtils.hasText(collaborator.getTargetAgentRef())) continue;
            ControlPlaneReferenceParser.ParsedReference parsed;
            try {
                parsed = references.parse(collaborator.getTargetAgentRef(), "agent");
            } catch (IllegalArgumentException ex) {
                report.error(ex.getMessage());
                continue;
            }
            AiAgentEntity target = findAgent(parsed.code());
            Integer requested = parsed.version() == null && target != null ? target.getCurrentVersion() : parsed.version();
            AiAgentVersionEntity published = findPublishedVersion(parsed.code(), requested);
            if (target == null || published == null || !Boolean.TRUE.equals(target.getEnabled())) {
                report.error("collaborator must reference a published enabled Agent: "
                        + collaborator.getTargetAgentRef());
            } else if (freezeVersions) {
                collaborator.setTargetAgentRef(references.freeze(
                        collaborator.getTargetAgentRef(), "agent", published.getVersionNo()));
            }
        }
    }

    private void freezeWorkflowRef(AgentControlDTOs.Output output,
                                   ValidationReportDTO report,
                                   boolean freezeVersions) {
        if (output == null || !StringUtils.hasText(output.getWorkflowRef())) return;
        ControlPlaneReferenceParser.ParsedReference parsed;
        try {
            parsed = references.parse(output.getWorkflowRef(), "workflow");
        } catch (IllegalArgumentException ex) {
            report.error(ex.getMessage());
            return;
        }
        AiChatWorkflowVersionEntity workflow = findPublishedWorkflow(parsed.code(), parsed.version());
        if (workflow == null) {
            report.error("workflowRef must reference a published Workflow: " + output.getWorkflowRef());
        } else if (freezeVersions) {
            output.setWorkflowRef(references.freeze(output.getWorkflowRef(), "workflow", workflow.getVersionNo()));
        }
    }

    private StoredAgentDefinition toStoredDefinition(AiAgentEntity agent,
                                                     AiAgentVersionEntity version,
                                                     AiAgentEntryBindingEntity binding) {
        AgentControlDTOs.Manifest manifest = json.read(version.getManifestJson(), AgentControlDTOs.Manifest.class);
        ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType runtimeType = binding == null
                ? ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType.OPENAI_AGENTS_PYTHON
                : ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType.valueOf(binding.getRuntimeType().name());
        String sdkVersion = binding == null ? "inherit-root" : binding.getSdkVersion();
        return StoredAgentDefinition.builder()
                .agentCode(agent.getCode())
                .agentVersion(version.getVersionNo())
                .name(agent.getName())
                .description(agent.getDescription())
                .manifestJson(version.getManifestJson())
                .runtimeType(runtimeType)
                .sdkVersion(sdkVersion)
                .checksum(version.getChecksum())
                .resolvedCapabilitiesJson(resolveCapabilitiesSnapshot(manifest))
                .workflowSnapshotJson(resolveWorkflowSnapshot(manifest))
                .build();
    }

    private String resolveCapabilitiesSnapshot(AgentControlDTOs.Manifest manifest) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        AgentControlDTOs.Spec spec = manifest == null ? null : manifest.getSpec();
        List<Map<String, Object>> skills = new ArrayList<>();
        if (spec != null && spec.getSkillRefs() != null) {
            for (AgentControlDTOs.CapabilityRef ref : spec.getSkillRefs()) {
                if (ref == null || Boolean.FALSE.equals(ref.getEnabled())) continue;
                ControlPlaneReferenceParser.ParsedReference parsed = references.parse(ref.getRef(), "skill");
                Integer requestedVersion = ref.getVersion() == null ? parsed.version() : ref.getVersion();
                AiChatSkillVersionEntity version = findPublishedSkill(parsed.code(), requestedVersion);
                if (version != null) {
                    Map<String, Object> skillManifest = json.readMap(version.getManifestJson());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ref", "skill://" + version.getSkillCode() + "/v" + version.getVersionNo());
                    item.put("code", version.getSkillCode());
                    item.put("version", version.getVersionNo());
                    item.put("name", skillManifest.getOrDefault("name", version.getSkillCode()));
                    item.put("description", skillManifest.getOrDefault("description", ""));
                    item.put("entrypoint", version.getEntrypoint());
                    item.put("checksum", version.getPackageChecksum());
                    item.put("contentHash", version.getPackageChecksum());
                    item.put("manifest", skillManifest);
                    item.put("files", inlineSkillFiles(version.getId()));
                    skills.add(item);
                }
            }
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        if (spec != null && spec.getToolRefs() != null) {
            for (AgentControlDTOs.CapabilityRef ref : spec.getToolRefs()) {
                if (ref == null || Boolean.FALSE.equals(ref.getEnabled())) continue;
                ControlPlaneReferenceParser.ParsedReference parsed = references.parse(ref.getRef(), "tool");
                Integer requestedVersion = ref.getVersion() == null ? parsed.version() : ref.getVersion();
                AiChatToolVersionEntity version = findPublishedTool(parsed.code(), requestedVersion);
                if (version != null) {
                    Map<String, Object> definition = json.readMap(version.getDefinitionJson());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ref", "tool://" + version.getToolCode() + "/v" + version.getVersionNo());
                    item.put("code", version.getToolCode());
                    item.put("version", version.getVersionNo());
                    item.put("name", StringUtils.hasText(ref.getAlias()) ? ref.getAlias() : version.getToolCode());
                    item.put("description", definition.getOrDefault("description", ""));
                    item.put("adapterType", version.getAdapterType().name());
                    item.put("checksum", version.getChecksum());
                    item.put("contentHash", version.getChecksum());
                    item.put("definition", definition);
                    tools.add(item);
                }
            }
        }
        List<Map<String, Object>> collaborators = new ArrayList<>();
        if (spec != null && spec.getCollaboration() != null) {
            List<AgentControlDTOs.CollaboratorRef> collaboratorRefs = new ArrayList<>();
            if (spec.getCollaboration().getAgentTools() != null) {
                collaboratorRefs.addAll(spec.getCollaboration().getAgentTools());
            }
            if (spec.getCollaboration().getHandoffs() != null) {
                collaboratorRefs.addAll(spec.getCollaboration().getHandoffs());
            }
            for (AgentControlDTOs.CollaboratorRef ref : collaboratorRefs) {
                if (ref == null) continue;
                ControlPlaneReferenceParser.ParsedReference parsed = references.parse(ref.getTargetAgentRef(), "agent");
                AiAgentVersionEntity version = findPublishedVersion(parsed.code(), parsed.version());
                AiAgentEntity agent = findAgent(parsed.code());
                if (version != null && agent != null && Boolean.TRUE.equals(agent.getEnabled())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    String canonicalRef = "agent://" + agent.getCode() + "/v" + version.getVersionNo();
                    item.put("ref", canonicalRef);
                    item.put("agentRef", canonicalRef);
                    item.put("code", agent.getCode());
                    item.put("name", agent.getName());
                    item.put("version", version.getVersionNo());
                    item.put("mode", ref.getMode());
                    item.put("toolName", ref.getToolName());
                    item.put("checksum", version.getChecksum());
                    item.put("manifest", json.readMap(version.getManifestJson()));
                    collaborators.add(item);
                }
            }
        }
        snapshot.put("skills", skills);
        snapshot.put("tools", tools);
        snapshot.put("collaborators", collaborators);
        return json.write(snapshot);
    }

    private Map<String, String> inlineSkillFiles(Long skillVersionId) {
        if (skillVersionId == null) return Map.of();
        List<AiChatSkillFileEntity> files = skillFileMapper.selectList(
                Wrappers.<AiChatSkillFileEntity>lambdaQuery()
                        .eq(AiChatSkillFileEntity::getSkillVersionId, skillVersionId)
                        .orderByAsc(AiChatSkillFileEntity::getPath));
        Map<String, String> inline = new LinkedHashMap<>();
        long total = 0;
        for (AiChatSkillFileEntity file : files) {
            byte[] content = file.getContent();
            if (content == null || content.length > MAX_INLINE_SKILL_FILE_BYTES
                    || !isTextMediaType(file.getMediaType())) {
                continue;
            }
            if (total + content.length > MAX_INLINE_SKILL_SNAPSHOT_BYTES) break;
            inline.put(file.getPath(), new String(content, StandardCharsets.UTF_8));
            total += content.length;
        }
        return inline;
    }

    private boolean isTextMediaType(String mediaType) {
        if (!StringUtils.hasText(mediaType)) return false;
        String value = mediaType.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("text/") || value.contains("json") || value.contains("yaml")
                || value.contains("xml") || value.contains("javascript");
    }

    private String resolveWorkflowSnapshot(AgentControlDTOs.Manifest manifest) {
        if (manifest == null || manifest.getSpec() == null || manifest.getSpec().getOutput() == null
                || !StringUtils.hasText(manifest.getSpec().getOutput().getWorkflowRef())) {
            return "{}";
        }
        ControlPlaneReferenceParser.ParsedReference parsed = references.parse(
                manifest.getSpec().getOutput().getWorkflowRef(), "workflow");
        AiChatWorkflowVersionEntity version = findPublishedWorkflow(parsed.code(), parsed.version());
        if (version == null) {
            return "{}";
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(json.readMap(version.getSpecificationJson()));
        snapshot.put("workflowRef", "workflow://" + version.getWorkflowCode() + "/v" + version.getVersionNo());
        snapshot.put("contentHash", version.getChecksum());
        return json.write(snapshot);
    }

    private AiChatSkillVersionEntity findPublishedSkill(String code, Integer version) {
        if (!StringUtils.hasText(code)) return null;
        var query = Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code.trim())
                .eq(AiChatSkillVersionEntity::getStatus, DefinitionStatus.PUBLISHED);
        if (version != null) query.eq(AiChatSkillVersionEntity::getVersionNo, version);
        else query.orderByDesc(AiChatSkillVersionEntity::getVersionNo).last("LIMIT 1");
        return skillVersionMapper.selectOne(query);
    }

    private AiChatToolVersionEntity findPublishedTool(String code, Integer version) {
        if (!StringUtils.hasText(code)) return null;
        var query = Wrappers.<AiChatToolVersionEntity>lambdaQuery()
                .eq(AiChatToolVersionEntity::getToolCode, code.trim())
                .eq(AiChatToolVersionEntity::getStatus, DefinitionStatus.PUBLISHED);
        if (version != null) query.eq(AiChatToolVersionEntity::getVersionNo, version);
        else query.orderByDesc(AiChatToolVersionEntity::getVersionNo).last("LIMIT 1");
        return toolVersionMapper.selectOne(query);
    }

    private AiChatWorkflowVersionEntity findPublishedWorkflow(String code, Integer version) {
        if (!StringUtils.hasText(code)) return null;
        var query = Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code.trim())
                .eq(AiChatWorkflowVersionEntity::getStatus, DefinitionStatus.PUBLISHED);
        if (version != null) query.eq(AiChatWorkflowVersionEntity::getVersionNo, version);
        else query.orderByDesc(AiChatWorkflowVersionEntity::getVersionNo).last("LIMIT 1");
        return workflowVersionMapper.selectOne(query);
    }

    private AiAgentVersionEntity requirePublishedVersion(String code, Integer version) {
        AiAgentVersionEntity entity = findPublishedVersion(code, version);
        if (entity == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private AiAgentVersionEntity findPublishedVersion(String code, Integer version) {
        if (!StringUtils.hasText(code) || version == null) return null;
        return versionMapper.selectOne(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                .eq(AiAgentVersionEntity::getAgentCode, code.trim())
                .eq(AiAgentVersionEntity::getVersionNo, version)
                .eq(AiAgentVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
    }

    private AiAgentEntryBindingEntity firstEnabledBinding(String code, Integer version) {
        return bindingMapper.selectOne(Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                .eq(AiAgentEntryBindingEntity::getAgentCode, code)
                .eq(AiAgentEntryBindingEntity::getAgentVersion, version)
                .eq(AiAgentEntryBindingEntity::getEnabled, Boolean.TRUE)
                .orderByAsc(AiAgentEntryBindingEntity::getPriority)
                .orderByAsc(AiAgentEntryBindingEntity::getId)
                .last("LIMIT 1"));
    }

    private List<AiAgentEntryBindingEntity> enabledEntryBindings(String entryCode) {
        return bindingMapper.selectList(Wrappers.<AiAgentEntryBindingEntity>lambdaQuery()
                .eq(AiAgentEntryBindingEntity::getEntryCode, entryCode)
                .eq(AiAgentEntryBindingEntity::getEnabled, Boolean.TRUE)
                .orderByAsc(AiAgentEntryBindingEntity::getPriority)
                .orderByAsc(AiAgentEntryBindingEntity::getId));
    }

    private AiAgentVersionEntity requireVersion(String code, Integer version) {
        if (version == null || version < 1) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        AiAgentVersionEntity entity = versionMapper.selectOne(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                .eq(AiAgentVersionEntity::getAgentCode, code)
                .eq(AiAgentVersionEntity::getVersionNo, version));
        if (entity == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private AiAgentEntity requireAgent(String code) {
        AiAgentEntity entity = findAgent(code);
        if (entity == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private AiAgentEntity findAgent(String code) {
        if (!StringUtils.hasText(code)) return null;
        return agentMapper.selectOne(Wrappers.<AiAgentEntity>lambdaQuery()
                .eq(AiAgentEntity::getCode, code.trim()));
    }

    private int nextVersion(String code) {
        AiAgentVersionEntity latest = versionMapper.selectOne(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                .eq(AiAgentVersionEntity::getAgentCode, code)
                .orderByDesc(AiAgentVersionEntity::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void enrichManifestIdentity(AiAgentEntity agent, Integer version, AgentControlDTOs.Manifest manifest) {
        if (manifest.getMetadata() == null) {
            manifest.setMetadata(new AgentControlDTOs.Metadata());
        }
        manifest.getMetadata().setCode(agent.getCode());
        manifest.getMetadata().setVersion(version);
        manifest.getMetadata().setName(agent.getName());
        manifest.getMetadata().setDescription(agent.getDescription());
        if (!StringUtils.hasText(manifest.getApiVersion())) manifest.setApiVersion("ai.platform/v1alpha1");
        manifest.setKind("Agent");
    }

    private AgentControlDTOs.Catalog toCatalogDTO(AiAgentEntity entity) {
        AgentControlDTOs.Catalog dto = new AgentControlDTOs.Catalog();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCurrentVersion(entity.getCurrentVersion());
        dto.setCurrentPublishedVersion(entity.getCurrentVersion());
        AiAgentVersionEntity draft = latestMutableVersion(entity.getCode());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setEnabled(entity.getEnabled());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private AgentControlDTOs.Version toVersionDTO(AiAgentVersionEntity entity) {
        AgentControlDTOs.Version dto = new AgentControlDTOs.Version();
        AiAgentEntity agent = requireAgent(entity.getAgentCode());
        AgentControlDTOs.Manifest manifest = json.read(entity.getManifestJson(), AgentControlDTOs.Manifest.class);
        dto.setId(entity.getId());
        dto.setAgentCode(entity.getAgentCode());
        dto.setCode(entity.getAgentCode());
        dto.setName(agent.getName());
        dto.setDescription(agent.getDescription());
        dto.setEnabled(agent.getEnabled());
        dto.setVersion(entity.getVersionNo());
        dto.setDraftVersion(entity.getStatus() == DefinitionStatus.PUBLISHED ? null : entity.getVersionNo());
        dto.setCurrentPublishedVersion(agent.getCurrentVersion());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setManifest(manifest);
        dto.setApiVersion(manifest == null ? null : manifest.getApiVersion());
        dto.setKind(manifest == null ? null : manifest.getKind());
        dto.setMetadata(manifest == null ? null : manifest.getMetadata());
        dto.setSpec(manifest == null ? null : manifest.getSpec());
        dto.setValidation(json.read(entity.getValidationJson(), ValidationReportDTO.class));
        dto.setChecksum(entity.getChecksum());
        dto.setPublishedAt(entity.getPublishedAt());
        return dto;
    }

    private AgentControlDTOs.EntrySelection toEntrySelection(AiAgentEntryBindingEntity entity) {
        AgentControlDTOs.EntrySelection dto = new AgentControlDTOs.EntrySelection();
        Map<String, Object> config = json.readMap(entity.getConfigJson());
        String strategy = config.get("versionStrategy") instanceof String value
                ? value : "PINNED";
        dto.setEntryCode(entity.getEntryCode());
        dto.setAgentCode(entity.getAgentCode());
        dto.setVersionStrategy(strategy);
        dto.setPinnedVersion("PINNED".equals(strategy) ? entity.getAgentVersion() : null);
        dto.setEnabled(entity.getEnabled());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private AiAgentVersionEntity latestMutableVersion(String code) {
        return versionMapper.selectOne(Wrappers.<AiAgentVersionEntity>lambdaQuery()
                .eq(AiAgentVersionEntity::getAgentCode, code)
                .ne(AiAgentVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .ne(AiAgentVersionEntity::getStatus, DefinitionStatus.ARCHIVED)
                .orderByDesc(AiAgentVersionEntity::getVersionNo)
                .last("LIMIT 1"));
    }

    private AiAgentVersionEntity latestEditableOrPublishedVersion(AiAgentEntity agent) {
        AiAgentVersionEntity draft = latestMutableVersion(agent.getCode());
        if (draft != null) return draft;
        return findPublishedVersion(agent.getCode(), agent.getCurrentVersion());
    }

    private AgentControlDTOs.Manifest manifestOf(AgentControlDTOs.CreateRequest request) {
        if (request == null) return null;
        return request.getManifest() != null ? request.getManifest()
                : manifestOf(request.getApiVersion(), request.getKind(), request.getMetadata(), request.getSpec());
    }

    private AgentControlDTOs.Manifest manifestOf(AgentControlDTOs.UpdateRequest request) {
        if (request == null) return null;
        return request.getManifest() != null ? request.getManifest()
                : manifestOf(request.getApiVersion(), request.getKind(), request.getMetadata(), request.getSpec());
    }

    private AgentControlDTOs.Manifest manifestOf(AgentControlDTOs.VersionCreateRequest request) {
        if (request == null) return null;
        return request.getManifest() != null ? request.getManifest()
                : manifestOf(request.getApiVersion(), request.getKind(), request.getMetadata(), request.getSpec());
    }

    private AgentControlDTOs.Manifest manifestOf(String apiVersion,
                                                 String kind,
                                                 AgentControlDTOs.Metadata metadata,
                                                 AgentControlDTOs.Spec spec) {
        if (spec == null) return null;
        AgentControlDTOs.Manifest manifest = new AgentControlDTOs.Manifest();
        manifest.setApiVersion(StringUtils.hasText(apiVersion) ? apiVersion.trim() : "ai.platform/v1alpha1");
        manifest.setKind(StringUtils.hasText(kind) ? kind.trim() : "Agent");
        manifest.setMetadata(metadata == null ? new AgentControlDTOs.Metadata() : metadata);
        manifest.setSpec(spec);
        return manifest;
    }

    private AgentControlDTOs.EntryBinding toBindingDTO(AiAgentEntryBindingEntity entity) {
        AgentControlDTOs.EntryBinding dto = new AgentControlDTOs.EntryBinding();
        dto.setId(entity.getId());
        dto.setEntryCode(entity.getEntryCode());
        dto.setAgentCode(entity.getAgentCode());
        dto.setAgentVersion(entity.getAgentVersion());
        dto.setRuntimeType(entity.getRuntimeType());
        dto.setSdkVersion(entity.getSdkVersion());
        dto.setPriority(entity.getPriority());
        dto.setEnabled(entity.getEnabled());
        dto.setConfig(json.readMap(entity.getConfigJson()));
        return dto;
    }

    private boolean containsSensitiveKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT).replace("-", "_");
                if (SENSITIVE_CONFIG_KEYS.contains(key) || containsSensitiveKey(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (containsSensitiveKey(item)) return true;
            }
        }
        return false;
    }

    private void requireCreateRequest(AgentControlDTOs.CreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())
                || !StringUtils.hasText(request.getName()) || manifestOf(request) == null) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9._-]{1,64}")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim();
    }

    private String normalizeEntryCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9._-]{1,64}")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
