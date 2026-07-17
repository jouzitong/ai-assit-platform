package ai.platform.aiassit.chat.agent.control.data.service.control.impl;

import ai.platform.aiassit.chat.agent.control.data.entity.AiChatWorkflowEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiChatWorkflowVersionEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.config.WorkflowCatalogConfig;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.WorkflowControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatWorkflowMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatWorkflowVersionMapper;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiWorkflowControlService;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneJsonSupport;
import ai.platform.aiassit.chat.agent.control.data.validator.WorkflowSpecificationValidator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiWorkflowControlServiceImpl implements AiWorkflowControlService {

    private final AiChatWorkflowMapper workflowMapper;
    private final AiChatWorkflowVersionMapper versionMapper;
    private final WorkflowSpecificationValidator validator;
    private final ControlPlaneJsonSupport json;

    public AiWorkflowControlServiceImpl(AiChatWorkflowMapper workflowMapper,
                                        AiChatWorkflowVersionMapper versionMapper,
                                        WorkflowSpecificationValidator validator,
                                        ControlPlaneJsonSupport json) {
        this.workflowMapper = workflowMapper;
        this.versionMapper = versionMapper;
        this.validator = validator;
        this.json = json;
    }

    @Override
    public List<WorkflowControlDTOs.Catalog> listCatalogs() {
        return workflowMapper.selectList(Wrappers.<AiChatWorkflowEntity>lambdaQuery()
                        .orderByDesc(AiChatWorkflowEntity::getUpdateTime)
                        .orderByDesc(AiChatWorkflowEntity::getId))
                .stream().map(this::toCatalogDTO).toList();
    }

    @Override
    public WorkflowControlDTOs.Version getWorkflow(String workflowCode) {
        String code = normalizeCode(workflowCode);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        AiChatWorkflowVersionEntity version = latestMutable(code);
        if (version == null) version = latestPublished(code);
        if (version == null) version = latest(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowControlDTOs.Version createDraft(WorkflowControlDTOs.DraftRequest request) {
        String code = requestCode(request);
        if (findCatalog(code) != null) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
        AiChatWorkflowEntity catalog = new AiChatWorkflowEntity();
        catalog.setCode(code);
        catalog.setName(requestName(request));
        catalog.setType("ARTIFACT");
        catalog.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        catalog.setConfig(catalogConfig(code, requestDescription(request)));
        workflowMapper.insert(catalog);
        AiChatWorkflowVersionEntity version = persistVersion(catalog, request);
        log.info("Artifact Workflow created: workflowCode={}, version={}, result=success", code, version.getVersionNo());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowControlDTOs.Version createVersion(String workflowCode,
                                                      WorkflowControlDTOs.DraftRequest request) {
        String code = normalizeCode(workflowCode);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        alignPathIdentity(request, code, catalog.getName());
        AiChatWorkflowVersionEntity version = persistVersion(catalog, request);
        log.info("Artifact Workflow version created: workflowCode={}, version={}, result=success",
                code, version.getVersionNo());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowControlDTOs.Version updateWorkflow(String workflowCode,
                                                       WorkflowControlDTOs.DraftRequest request) {
        String code = normalizeCode(workflowCode);
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        String name = StringUtils.hasText(requestNameOrNull(request)) ? requestName(request) : catalog.getName();
        catalog.setName(name);
        if (request.getEnabled() != null) catalog.setEnabled(request.getEnabled());
        WorkflowCatalogConfig config = catalog.getConfig() == null
                ? catalogConfig(code, null) : catalog.getConfig();
        if (StringUtils.hasText(requestDescription(request))) config.setSceneDesc(requestDescription(request));
        catalog.setConfig(config);
        workflowMapper.updateById(catalog);

        AiChatWorkflowVersionEntity version = latestMutable(code);
        if (hasDefinition(request)) {
            if (version == null) {
                alignPathIdentity(request, code, name);
                version = persistVersion(catalog, request);
            } else {
                WorkflowControlDTOs.Manifest manifest = manifestOf(request, code, version.getVersionNo(), catalog);
                String content = json.write(manifest);
                version.setSpecificationJson(content);
                version.setChecksum(json.sha256(content));
                version.setValidationJson(null);
                version.setStatus(DefinitionStatus.DRAFT);
                versionMapper.updateById(version);
            }
        }
        if (version == null) version = latestPublished(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        log.info("Artifact Workflow updated: workflowCode={}, enabled={}, result=success", code, catalog.getEnabled());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWorkflow(String workflowCode) {
        String code = normalizeCode(workflowCode);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        long published = versionMapper.selectCount(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                .eq(AiChatWorkflowVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
        if (published > 0) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
        versionMapper.delete(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code));
        boolean deleted = workflowMapper.deleteById(catalog.getId()) > 0;
        log.info("Artifact Workflow deleted: workflowCode={}, result={}", code, deleted ? "success" : "not_changed");
        return deleted;
    }

    @Override
    public List<WorkflowControlDTOs.Version> listVersions(String workflowCode) {
        String code = normalizeCode(workflowCode);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        return versionMapper.selectList(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                        .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                        .orderByDesc(AiChatWorkflowVersionEntity::getVersionNo))
                .stream().map(item -> toDTO(item, catalog)).toList();
    }

    @Override
    public WorkflowControlDTOs.Version getVersion(String workflowCode, Integer version) {
        String code = normalizeCode(workflowCode);
        return toDTO(requireVersion(code, version), requireCatalog(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ValidationReportDTO validateVersion(String workflowCode, Integer version) {
        AiChatWorkflowVersionEntity entity = requireVersion(normalizeCode(workflowCode), version);
        WorkflowControlDTOs.Manifest manifest = readManifest(entity);
        ValidationReportDTO report = validator.validate(manifest);
        entity.setValidationJson(json.write(report));
        if (entity.getStatus() != DefinitionStatus.PUBLISHED) {
            entity.setStatus(report.isValid() ? DefinitionStatus.VALIDATED : DefinitionStatus.DRAFT);
        }
        versionMapper.updateById(entity);
        log.info("Artifact Workflow validated: workflowCode={}, version={}, valid={}, errorCount={}",
                entity.getWorkflowCode(), entity.getVersionNo(), report.isValid(), report.getErrors().size());
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowControlDTOs.Version publishVersion(String workflowCode, Integer version) {
        String code = normalizeCode(workflowCode);
        AiChatWorkflowVersionEntity entity = requireVersion(code, version);
        AiChatWorkflowEntity catalog = requireCatalog(code);
        if (entity.getStatus() == DefinitionStatus.PUBLISHED) return toDTO(entity, catalog);
        WorkflowControlDTOs.Manifest manifest = readManifest(entity);
        ValidationReportDTO report = validator.validate(manifest);
        if (!report.isValid()) {
            entity.setValidationJson(json.write(report));
            entity.setStatus(DefinitionStatus.DRAFT);
            versionMapper.updateById(entity);
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        String frozen = json.write(manifest);
        entity.setSpecificationJson(frozen);
        entity.setChecksum(json.sha256(frozen));
        entity.setValidationJson(json.write(report));
        entity.setStatus(DefinitionStatus.PUBLISHED);
        entity.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(entity);
        catalog.setEnabled(Boolean.TRUE);
        WorkflowCatalogConfig config = catalog.getConfig() == null ? catalogConfig(code, null) : catalog.getConfig();
        config.getExt().put("currentPublishedVersion", version);
        config.getExt().put("definitionMode", "ARTIFACT_CONTRACT");
        catalog.setConfig(config);
        workflowMapper.updateById(catalog);
        log.info("Artifact Workflow published: workflowCode={}, version={}, result=success", code, version);
        return toDTO(entity, catalog);
    }

    @Override
    public Map<String, Object> testVersion(String workflowCode, Integer version, Map<String, Object> payload) {
        AiChatWorkflowVersionEntity entity = requireVersion(normalizeCode(workflowCode), version);
        ValidationReportDTO report = validator.validate(readManifest(entity));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowCode", entity.getWorkflowCode());
        result.put("version", entity.getVersionNo());
        result.put("valid", report.isValid());
        result.put("validation", report);
        result.put("input", payload == null ? Map.of() : payload);
        result.put("mode", "VALIDATION_DRY_RUN");
        return result;
    }

    private AiChatWorkflowVersionEntity persistVersion(AiChatWorkflowEntity catalog,
                                                        WorkflowControlDTOs.DraftRequest request) {
        int versionNo = nextVersion(catalog.getCode());
        WorkflowControlDTOs.Manifest manifest = manifestOf(request, catalog.getCode(), versionNo, catalog);
        String specificationJson = json.write(manifest);
        AiChatWorkflowVersionEntity version = new AiChatWorkflowVersionEntity();
        version.setWorkflowCode(catalog.getCode());
        version.setVersionNo(versionNo);
        version.setStatus(DefinitionStatus.DRAFT);
        version.setSpecificationJson(specificationJson);
        version.setChecksum(json.sha256(specificationJson));
        versionMapper.insert(version);
        return version;
    }

    private WorkflowControlDTOs.Manifest manifestOf(WorkflowControlDTOs.DraftRequest request,
                                                     String code,
                                                     Integer version,
                                                     AiChatWorkflowEntity catalog) {
        WorkflowControlDTOs.Manifest manifest = new WorkflowControlDTOs.Manifest();
        manifest.setApiVersion(StringUtils.hasText(request.getApiVersion())
                ? request.getApiVersion().trim() : "ai.platform/v1alpha1");
        manifest.setKind("ArtifactWorkflow");
        WorkflowControlDTOs.Metadata metadata = request.getMetadata() == null
                ? new WorkflowControlDTOs.Metadata() : request.getMetadata();
        metadata.setCode(code);
        metadata.setVersion(version);
        metadata.setName(catalog.getName());
        metadata.setDescription(catalogDescription(catalog));
        manifest.setMetadata(metadata);
        WorkflowControlDTOs.Spec spec = request.getSpec();
        if (spec == null) {
            spec = new WorkflowControlDTOs.Spec();
            spec.setArtifacts(request.getArtifacts());
            spec.setChecks(request.getChecks());
            spec.setCompletionPolicy(request.getCompletionPolicy());
            spec.setRepairPolicy(request.getRepairPolicy());
        }
        manifest.setSpec(spec);
        return manifest;
    }

    private WorkflowControlDTOs.Manifest readManifest(AiChatWorkflowVersionEntity entity) {
        WorkflowControlDTOs.Manifest manifest = json.read(entity.getSpecificationJson(), WorkflowControlDTOs.Manifest.class);
        if (manifest == null || manifest.getSpec() == null) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return manifest;
    }

    private WorkflowControlDTOs.Version toDTO(AiChatWorkflowVersionEntity entity, AiChatWorkflowEntity catalog) {
        WorkflowControlDTOs.Manifest manifest = readManifest(entity);
        WorkflowControlDTOs.Version dto = new WorkflowControlDTOs.Version();
        dto.setId(entity.getId());
        dto.setWorkflowCode(entity.getWorkflowCode());
        dto.setCode(entity.getWorkflowCode());
        dto.setName(catalog.getName());
        dto.setDescription(catalogDescription(catalog));
        dto.setEnabled(catalog.getEnabled());
        dto.setVersion(entity.getVersionNo());
        AiChatWorkflowVersionEntity published = latestPublished(entity.getWorkflowCode());
        AiChatWorkflowVersionEntity draft = latestMutable(entity.getWorkflowCode());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setManifest(manifest);
        dto.setApiVersion(manifest.getApiVersion());
        dto.setKind(manifest.getKind());
        dto.setMetadata(manifest.getMetadata());
        dto.setSpec(manifest.getSpec());
        dto.setArtifacts(manifest.getSpec().getArtifacts());
        dto.setChecks(manifest.getSpec().getChecks());
        dto.setCompletionPolicy(manifest.getSpec().getCompletionPolicy());
        dto.setRepairPolicy(manifest.getSpec().getRepairPolicy());
        dto.setValidation(json.read(entity.getValidationJson(), ValidationReportDTO.class));
        dto.setChecksum(entity.getChecksum());
        dto.setPublishedAt(entity.getPublishedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private WorkflowControlDTOs.Catalog toCatalogDTO(AiChatWorkflowEntity entity) {
        WorkflowControlDTOs.Catalog dto = new WorkflowControlDTOs.Catalog();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(catalogDescription(entity));
        dto.setEnabled(entity.getEnabled());
        AiChatWorkflowVersionEntity published = latestPublished(entity.getCode());
        AiChatWorkflowVersionEntity draft = latestMutable(entity.getCode());
        AiChatWorkflowVersionEntity latest = draft != null ? draft : published;
        dto.setStatus(latest == null || latest.getStatus() == null ? null : latest.getStatus().name());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private AiChatWorkflowVersionEntity requireVersion(String code, Integer version) {
        if (version == null || version < 1) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatWorkflowVersionEntity entity = versionMapper.selectOne(
                Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                        .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                        .eq(AiChatWorkflowVersionEntity::getVersionNo, version));
        if (entity == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return entity;
    }

    private AiChatWorkflowEntity requireCatalog(String code) {
        AiChatWorkflowEntity entity = findCatalog(code);
        if (entity == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return entity;
    }

    private AiChatWorkflowEntity findCatalog(String code) {
        return workflowMapper.selectOne(Wrappers.<AiChatWorkflowEntity>lambdaQuery()
                .eq(AiChatWorkflowEntity::getCode, code));
    }

    private AiChatWorkflowVersionEntity latest(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                .orderByDesc(AiChatWorkflowVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatWorkflowVersionEntity latestMutable(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                .ne(AiChatWorkflowVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .ne(AiChatWorkflowVersionEntity::getStatus, DefinitionStatus.ARCHIVED)
                .orderByDesc(AiChatWorkflowVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatWorkflowVersionEntity latestPublished(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatWorkflowVersionEntity>lambdaQuery()
                .eq(AiChatWorkflowVersionEntity::getWorkflowCode, code)
                .eq(AiChatWorkflowVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .orderByDesc(AiChatWorkflowVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private int nextVersion(String code) {
        AiChatWorkflowVersionEntity value = latest(code);
        return value == null ? 1 : value.getVersionNo() + 1;
    }

    private boolean hasDefinition(WorkflowControlDTOs.DraftRequest request) {
        return request.getSpec() != null || (request.getArtifacts() != null && !request.getArtifacts().isEmpty());
    }

    private void alignPathIdentity(WorkflowControlDTOs.DraftRequest request, String code, String name) {
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        request.setCode(code);
        if (!StringUtils.hasText(requestNameOrNull(request))) request.setName(name);
        if (request.getMetadata() != null) request.getMetadata().setCode(code);
    }

    private String requestCode(WorkflowControlDTOs.DraftRequest request) {
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        String code = StringUtils.hasText(request.getCode()) ? request.getCode()
                : request.getMetadata() == null ? null : request.getMetadata().getCode();
        return normalizeCode(code);
    }

    private String requestName(WorkflowControlDTOs.DraftRequest request) {
        String name = requestNameOrNull(request);
        if (!StringUtils.hasText(name)) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        return name.trim();
    }

    private String requestNameOrNull(WorkflowControlDTOs.DraftRequest request) {
        if (request == null) return null;
        return StringUtils.hasText(request.getName()) ? request.getName()
                : request.getMetadata() == null ? null : request.getMetadata().getName();
    }

    private String requestDescription(WorkflowControlDTOs.DraftRequest request) {
        if (request == null) return null;
        String value = StringUtils.hasText(request.getDescription()) ? request.getDescription()
                : request.getMetadata() == null ? null : request.getMetadata().getDescription();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private WorkflowCatalogConfig catalogConfig(String code, String description) {
        WorkflowCatalogConfig config = new WorkflowCatalogConfig();
        config.setRouteKey(code);
        config.setSceneDesc(description);
        config.setTags(List.of("artifact", "agent-first"));
        return config;
    }

    private String catalogDescription(AiChatWorkflowEntity catalog) {
        return catalog.getConfig() == null ? null : catalog.getConfig().getSceneDesc();
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9._-]{1,64}")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim();
    }
}
