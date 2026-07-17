package ai.platform.aiassit.chat.agent.control.data.service.control.impl;

import ai.platform.aiassit.chat.agent.control.data.entity.AiChatSkillEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiChatSkillFileEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiChatSkillPackageEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiChatSkillVersionEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.SkillControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.enums.SkillSourceType;
import ai.platform.aiassit.chat.agent.control.data.importer.InspectedSkillPackage;
import ai.platform.aiassit.chat.agent.control.data.importer.SkillPackageDraftStore;
import ai.platform.aiassit.chat.agent.control.data.importer.SkillPackageInspector;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatSkillFileMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatSkillMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatSkillPackageMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiChatSkillVersionMapper;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiSkillControlService;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneJsonSupport;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class AiSkillControlServiceImpl implements AiSkillControlService {

    private final AiChatSkillMapper skillMapper;
    private final AiChatSkillVersionMapper versionMapper;
    private final AiChatSkillFileMapper fileMapper;
    private final AiChatSkillPackageMapper packageMapper;
    private final SkillPackageInspector inspector;
    private final SkillPackageDraftStore draftStore;
    private final ControlPlaneJsonSupport json;

    public AiSkillControlServiceImpl(AiChatSkillMapper skillMapper,
                                     AiChatSkillVersionMapper versionMapper,
                                     AiChatSkillFileMapper fileMapper,
                                     AiChatSkillPackageMapper packageMapper,
                                     SkillPackageInspector inspector,
                                     SkillPackageDraftStore draftStore,
                                     ControlPlaneJsonSupport json) {
        this.skillMapper = skillMapper;
        this.versionMapper = versionMapper;
        this.fileMapper = fileMapper;
        this.packageMapper = packageMapper;
        this.inspector = inspector;
        this.draftStore = draftStore;
        this.json = json;
    }

    @Override
    public List<SkillControlDTOs.Catalog> listCatalogs() {
        return skillMapper.selectList(Wrappers.<AiChatSkillEntity>lambdaQuery()
                        .orderByDesc(AiChatSkillEntity::getUpdateTime)
                        .orderByDesc(AiChatSkillEntity::getId))
                .stream().map(this::toCatalogDTO).toList();
    }

    @Override
    public SkillControlDTOs.Version getSkill(String skillCode) {
        String code = normalizePathCode(skillCode);
        AiChatSkillEntity catalog = requireCatalog(code);
        AiChatSkillVersionEntity version = latestMutable(code);
        if (version == null) version = latestPublished(code);
        if (version == null) version = latest(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return toDTO(version, catalog);
    }

    @Override
    public SkillControlDTOs.Inspection inspect(MultipartFile file) {
        InspectedSkillPackage inspected = inspector.inspect(file);
        String draftId = inspected.isValid() ? draftStore.quarantine(inspected) : null;
        return toInspectionDTO(inspected, draftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillControlDTOs.Version createFormDraft(SkillControlDTOs.FormDraftRequest request) {
        requireFormRequest(request);
        String code = normalizeNewCode(request.getCode());
        InspectedSkillPackage inspected = inspectGeneratedForm(code, request);
        if (!inspected.isValid()) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR,
                    String.join("; ", inspected.getErrors()));
        }
        AiChatSkillEntity catalog = upsertCatalog(code, request.getName(), description(request),
                body(request), request.getToolRefs(), request.getEnabled());
        Map<String, Object> manifest = buildManifest(SkillSourceType.FORM, inspected,
                request.getToolRefs(), request.getCompatibleRuntimes());
        AiChatSkillVersionEntity version = persistVersion(code, SkillSourceType.FORM, inspected, manifest,
                code + ".zip");
        log.info("Skill form draft created: skillCode={}, version={}, fileCount={}, packageSize={}, result=success",
                code, version.getVersionNo(), inspected.getFiles().size(), inspected.getTotalSize());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillControlDTOs.Version importDraft(String draftId, SkillControlDTOs.ImportRequest request) {
        InspectedSkillPackage inspected = draftStore.claim(draftId)
                .orElseThrow(() -> BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND));
        if (!inspected.isValid()) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        String code = StringUtils.hasText(request.getCode())
                ? normalizeNewCode(request.getCode()) : inspected.getSkillName();
        if (!code.equals(inspected.getSkillName())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR,
                    "Imported Skill code must match SKILL.md frontmatter name");
        }
        String name = StringUtils.hasText(request.getName()) ? request.getName().trim() : code;
        String description = StringUtils.hasText(request.getDescription())
                ? request.getDescription().trim() : inspected.getDescription();
        AiChatSkillEntity catalog = upsertCatalog(code, name, description, null,
                request.getToolRefs(), request.getEnabled());
        Map<String, Object> manifest = buildManifest(SkillSourceType.ZIP, inspected,
                request.getToolRefs(), request.getCompatibleRuntimes());
        AiChatSkillVersionEntity version = persistVersion(code, SkillSourceType.ZIP, inspected, manifest,
                code + ".zip");
        log.info("Skill package imported: skillCode={}, version={}, fileCount={}, packageSize={}, result=success",
                code, version.getVersionNo(), inspected.getFiles().size(), inspected.getTotalSize());
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillControlDTOs.Version updateSkill(String skillCode, SkillControlDTOs.UpdateRequest request) {
        String code = normalizePathCode(skillCode);
        if (request == null) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatSkillEntity catalog = requireCatalog(code);
        if (StringUtils.hasText(request.getName())) catalog.setName(request.getName().trim());
        if (request.getDescription() != null) catalog.setDesc(trimToNull(request.getDescription()));
        if (request.getToolRefs() != null) catalog.setToolRefs(new ArrayList<>(request.getToolRefs()));
        if (request.getEnabled() != null) catalog.setEnabled(request.getEnabled());
        if (StringUtils.hasText(body(request))) catalog.setContent(legacyPreview(body(request)));
        skillMapper.updateById(catalog);

        AiChatSkillVersionEntity version = latestMutable(code);
        if (StringUtils.hasText(body(request))) {
            SkillControlDTOs.FormDraftRequest form = request;
            form.setCode(code);
            if (!StringUtils.hasText(form.getName())) form.setName(catalog.getName());
            if (!StringUtils.hasText(form.getDescription())) form.setDescription(catalog.getDesc());
            InspectedSkillPackage inspected = inspectGeneratedForm(code, form);
            if (!inspected.isValid()) {
                throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR,
                        String.join("; ", inspected.getErrors()));
            }
            Map<String, Object> manifest = buildManifest(SkillSourceType.FORM, inspected,
                    request.getToolRefs(), request.getCompatibleRuntimes());
            if (version == null) {
                version = persistVersion(code, SkillSourceType.FORM, inspected, manifest, code + ".zip");
            } else {
                replaceVersion(version, SkillSourceType.FORM, inspected, manifest, code + ".zip");
            }
        }
        if (version == null) version = latestPublished(code);
        if (version == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return toDTO(version, catalog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkill(String skillCode) {
        String code = normalizePathCode(skillCode);
        AiChatSkillEntity catalog = requireCatalog(code);
        long published = versionMapper.selectCount(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .eq(AiChatSkillVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
        if (published > 0) throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        for (AiChatSkillVersionEntity version : versions(code)) deleteVersionContent(version.getId());
        versionMapper.delete(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code));
        boolean deleted = skillMapper.deleteById(catalog.getId()) > 0;
        log.info("Skill deleted: skillCode={}, result={}", code, deleted ? "success" : "not_changed");
        return deleted;
    }

    @Override
    public List<SkillControlDTOs.Version> listVersions(String skillCode) {
        String code = normalizePathCode(skillCode);
        AiChatSkillEntity catalog = requireCatalog(code);
        return versions(code).stream().map(item -> toDTO(item, catalog)).toList();
    }

    @Override
    public SkillControlDTOs.Version getVersion(String skillCode, Integer version) {
        String code = normalizePathCode(skillCode);
        return toDTO(requireVersion(code, version), requireCatalog(code));
    }

    @Override
    public List<SkillControlDTOs.FileItem> listVersionFiles(String skillCode, Integer version) {
        AiChatSkillVersionEntity entity = requireVersion(normalizePathCode(skillCode), version);
        return listFiles(entity.getId()).stream().map(this::toFileItem).toList();
    }

    @Override
    public PackageDownload getVersionPackage(String skillCode, Integer version) {
        AiChatSkillVersionEntity entity = requireVersion(normalizePathCode(skillCode), version);
        AiChatSkillPackageEntity value = requirePackage(entity.getId());
        return new PackageDownload(value.getOriginalFilename(), value.getPackageChecksum(), value.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ValidationReportDTO validateVersion(String skillCode, Integer version) {
        AiChatSkillVersionEntity entity = requireVersion(normalizePathCode(skillCode), version);
        ValidationReportDTO report = validateStoredPackage(entity);
        entity.setValidationJson(json.write(report));
        if (entity.getStatus() != DefinitionStatus.PUBLISHED) {
            entity.setStatus(report.isValid() ? DefinitionStatus.VALIDATED : DefinitionStatus.DRAFT);
        }
        versionMapper.updateById(entity);
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillControlDTOs.Version publishVersion(String skillCode, Integer version) {
        String code = normalizePathCode(skillCode);
        AiChatSkillVersionEntity entity = requireVersion(code, version);
        AiChatSkillEntity catalog = requireCatalog(code);
        if (entity.getStatus() == DefinitionStatus.PUBLISHED) return toDTO(entity, catalog);
        ValidationReportDTO report = validateStoredPackage(entity);
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
        catalog.setEnabled(Boolean.TRUE);
        catalog.setRemark("currentPublishedVersion=" + version);
        skillMapper.updateById(catalog);
        log.info("Skill published: skillCode={}, version={}, sourceType={}, result=success",
                code, version, entity.getSourceType());
        return toDTO(entity, catalog);
    }

    private AiChatSkillVersionEntity persistVersion(String code,
                                                     SkillSourceType sourceType,
                                                     InspectedSkillPackage inspected,
                                                     Map<String, Object> manifest,
                                                     String originalFilename) {
        AiChatSkillVersionEntity version = new AiChatSkillVersionEntity();
        version.setSkillCode(code);
        version.setVersionNo(nextVersion(code));
        version.setStatus(DefinitionStatus.DRAFT);
        populateVersion(version, sourceType, inspected, manifest);
        versionMapper.insert(version);
        persistVersionContent(version.getId(), inspected, originalFilename);
        return version;
    }

    private void replaceVersion(AiChatSkillVersionEntity version,
                                SkillSourceType sourceType,
                                InspectedSkillPackage inspected,
                                Map<String, Object> manifest,
                                String originalFilename) {
        if (version.getStatus() == DefinitionStatus.PUBLISHED || version.getStatus() == DefinitionStatus.ARCHIVED) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        populateVersion(version, sourceType, inspected, manifest);
        version.setStatus(DefinitionStatus.DRAFT);
        version.setValidationJson(null);
        versionMapper.updateById(version);
        deleteVersionContent(version.getId());
        persistVersionContent(version.getId(), inspected, originalFilename);
    }

    private void populateVersion(AiChatSkillVersionEntity version,
                                 SkillSourceType sourceType,
                                 InspectedSkillPackage inspected,
                                 Map<String, Object> manifest) {
        version.setSourceType(sourceType);
        version.setEntrypoint(inspected.getEntrypoint());
        version.setManifestJson(json.write(manifest));
        version.setPackageChecksum(packageChecksum(inspected.getFiles()));
        version.setPackageSize(inspected.getTotalSize());
    }

    private void persistVersionContent(Long versionId,
                                       InspectedSkillPackage inspected,
                                       String originalFilename) {
        for (InspectedSkillPackage.File inspectedFile : inspected.getFiles()) {
            AiChatSkillFileEntity file = new AiChatSkillFileEntity();
            file.setSkillVersionId(versionId);
            file.setPath(inspectedFile.getPath());
            file.setMediaType(inspectedFile.getMediaType());
            file.setContentSize(inspectedFile.getSize());
            file.setChecksum(inspectedFile.getChecksum());
            file.setContent(inspectedFile.getContent());
            fileMapper.insert(file);
        }
        byte[] raw = inspected.getOriginalPackage();
        if (raw == null || raw.length == 0) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatSkillPackageEntity packageEntity = new AiChatSkillPackageEntity();
        packageEntity.setSkillVersionId(versionId);
        packageEntity.setOriginalFilename(originalFilename);
        packageEntity.setPackageChecksum(inspected.getChecksum());
        packageEntity.setCompressedSize((long) raw.length);
        packageEntity.setContent(raw);
        packageMapper.insert(packageEntity);
    }

    private ValidationReportDTO validateStoredPackage(AiChatSkillVersionEntity version) {
        ValidationReportDTO report = new ValidationReportDTO();
        AiChatSkillPackageEntity packageEntity;
        try {
            packageEntity = requirePackage(version.getId());
        } catch (BizException ex) {
            report.error("original Skill ZIP is missing");
            report.finish();
            return report;
        }
        InspectedSkillPackage reinspected = inspector.inspect(packageEntity.getOriginalFilename(), packageEntity.getContent());
        reinspected.getErrors().forEach(report::error);
        reinspected.getWarnings().forEach(report::warn);
        if (!json.sha256(packageEntity.getContent()).equals(packageEntity.getPackageChecksum())) {
            report.error("original Skill ZIP checksum mismatch");
        }
        List<AiChatSkillFileEntity> files = listFiles(version.getId());
        if (files.size() != reinspected.getFiles().size()) report.error("persisted Skill file index is incomplete");
        Set<String> seen = new HashSet<>();
        long totalSize = 0;
        for (AiChatSkillFileEntity file : files) {
            String path = file.getPath();
            if (!StringUtils.hasText(path) || path.startsWith("/") || path.contains("../") || path.indexOf('\\') >= 0) {
                report.error("unsafe persisted path: " + path);
                continue;
            }
            if (!seen.add(path.toLowerCase(Locale.ROOT))) report.error("duplicate persisted path: " + path);
            byte[] content = file.getContent() == null ? new byte[0] : file.getContent();
            totalSize += content.length;
            if (content.length > SkillPackageInspector.MAX_FILE_BYTES) {
                report.error("persisted file exceeds per-file limit: " + path);
            }
            if (!json.sha256(content).equals(file.getChecksum())) {
                report.error("persisted file checksum mismatch: " + path);
            }
        }
        if (totalSize != version.getPackageSize()) report.error("persisted package size does not match version metadata");
        if (!packageChecksumFromEntities(files).equals(version.getPackageChecksum())) {
            report.error("persisted package checksum does not match version metadata");
        }
        Map<String, Object> manifest = json.readMap(version.getManifestJson());
        if (!packageEntity.getPackageChecksum().equals(manifest.get("originalPackageSha256"))) {
            report.error("Skill manifest originalPackageSha256 mismatch");
        }
        report.finish();
        return report;
    }

    private AiChatSkillEntity upsertCatalog(String code,
                                             String name,
                                             String description,
                                             String legacyContent,
                                             List<String> toolRefs,
                                             Boolean enabled) {
        AiChatSkillEntity catalog = findCatalog(code);
        if (catalog == null) {
            catalog = new AiChatSkillEntity();
            catalog.setCode(code);
        }
        catalog.setName(StringUtils.hasText(name) ? name.trim() : code);
        catalog.setDesc(trimToNull(description));
        catalog.setToolRefs(toolRefs == null ? new ArrayList<>() : new ArrayList<>(toolRefs));
        catalog.setContent(legacyPreview(legacyContent));
        catalog.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        if (catalog.getId() == null) skillMapper.insert(catalog);
        else skillMapper.updateById(catalog);
        return catalog;
    }

    private Map<String, Object> buildManifest(SkillSourceType sourceType,
                                              InspectedSkillPackage inspected,
                                              List<String> toolRefs,
                                              List<String> compatibleRuntimes) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "agent-skill");
        manifest.put("formatVersion", "1.0");
        manifest.put("name", inspected.getSkillName());
        manifest.put("description", inspected.getDescription());
        manifest.put("license", inspected.getLicense());
        manifest.put("compatibility", inspected.getCompatibility());
        manifest.put("sourceType", sourceType.name());
        manifest.put("packageRoot", inspected.getPackageRoot());
        manifest.put("entrypoint", inspected.getEntrypoint());
        manifest.put("originalPackageSha256", inspected.getChecksum());
        manifest.put("toolRefs", toolRefs == null ? List.of() : toolRefs);
        manifest.put("compatibleRuntimes", compatibleRuntimes == null ? List.of() : compatibleRuntimes);
        manifest.put("files", inspected.getFiles().stream().map(file -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", file.getPath());
            item.put("size", file.getSize());
            item.put("checksum", file.getChecksum());
            item.put("mediaType", file.getMediaType());
            return item;
        }).toList());
        return manifest;
    }

    private SkillControlDTOs.Version toDTO(AiChatSkillVersionEntity entity, AiChatSkillEntity catalog) {
        Map<String, Object> manifest = json.readMap(entity.getManifestJson());
        SkillControlDTOs.Version dto = new SkillControlDTOs.Version();
        dto.setId(entity.getId());
        dto.setSkillCode(entity.getSkillCode());
        dto.setCode(entity.getSkillCode());
        dto.setName(catalog.getName());
        dto.setDescription(catalog.getDesc());
        dto.setEnabled(catalog.getEnabled());
        dto.setVersion(entity.getVersionNo());
        AiChatSkillVersionEntity published = latestPublished(entity.getSkillCode());
        AiChatSkillVersionEntity draft = latestMutable(entity.getSkillCode());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setSourceType(entity.getSourceType() == null ? null : entity.getSourceType().name());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setEntrypoint(entity.getEntrypoint());
        dto.setLicense(text(manifest.get("license")));
        dto.setCompatibility(text(manifest.get("compatibility")));
        dto.setContent(skillMarkdownBody(entity));
        dto.setToolRefs(strings(manifest.get("toolRefs")));
        dto.setCompatibleRuntimes(strings(manifest.get("compatibleRuntimes")));
        dto.setManifest(manifest);
        dto.setFiles(listFiles(entity.getId()).stream().map(this::toFileItem).toList());
        dto.setValidation(json.read(entity.getValidationJson(), ValidationReportDTO.class));
        dto.setChecksum(entity.getPackageChecksum());
        AiChatSkillPackageEntity packageEntity = findPackage(entity.getId());
        dto.setPackageSha256(packageEntity == null ? null : packageEntity.getPackageChecksum());
        dto.setPackageSize(entity.getPackageSize());
        dto.setPublishedAt(entity.getPublishedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private SkillControlDTOs.Inspection toInspectionDTO(InspectedSkillPackage inspected, String draftId) {
        SkillControlDTOs.Inspection dto = new SkillControlDTOs.Inspection();
        dto.setDraftId(draftId);
        dto.setValid(inspected.isValid());
        dto.setEntrypoint(inspected.getEntrypoint());
        dto.setChecksum(inspected.getChecksum());
        dto.setTotalSize(inspected.getTotalSize());
        dto.setFiles(inspected.getFiles().stream().map(this::toFileItem).toList());
        dto.setErrors(new ArrayList<>(inspected.getErrors()));
        dto.setWarnings(new ArrayList<>(inspected.getWarnings()));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", inspected.getSkillName());
        manifest.put("description", inspected.getDescription());
        manifest.put("license", inspected.getLicense());
        manifest.put("compatibility", inspected.getCompatibility());
        manifest.put("packageRoot", inspected.getPackageRoot());
        manifest.put("entrypoint", inspected.getEntrypoint());
        dto.setManifest(manifest);
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("code", inspected.getSkillName());
        skill.put("name", inspected.getSkillName());
        skill.put("description", inspected.getDescription());
        skill.put("license", inspected.getLicense());
        skill.put("compatibility", inspected.getCompatibility());
        skill.put("sourceType", "ZIP");
        dto.setSkill(skill);
        ValidationReportDTO compatibility = new ValidationReportDTO();
        inspected.getErrors().forEach(compatibility::error);
        inspected.getWarnings().forEach(compatibility::warn);
        compatibility.finish();
        dto.setCompatibility(compatibility);
        dto.setRisks(new ArrayList<>(compatibility.getWarnings()));
        return dto;
    }

    private SkillControlDTOs.FileItem toFileItem(InspectedSkillPackage.File file) {
        SkillControlDTOs.FileItem dto = new SkillControlDTOs.FileItem();
        fillFile(dto, file.getPath(), file.getMediaType(), file.getSize(), file.getChecksum());
        return dto;
    }

    private SkillControlDTOs.FileItem toFileItem(AiChatSkillFileEntity file) {
        SkillControlDTOs.FileItem dto = new SkillControlDTOs.FileItem();
        fillFile(dto, file.getPath(), file.getMediaType(),
                file.getContentSize() == null ? 0 : file.getContentSize(), file.getChecksum());
        return dto;
    }

    private void fillFile(SkillControlDTOs.FileItem dto, String path, String mediaType, long size, String checksum) {
        dto.setPath(path);
        int separator = path == null ? -1 : path.lastIndexOf('/');
        dto.setName(separator < 0 ? path : path.substring(separator + 1));
        dto.setRole(fileRole(path));
        dto.setMediaType(mediaType);
        dto.setSize(size);
        dto.setChecksum(checksum);
    }

    private InspectedSkillPackage inspectGeneratedForm(String code, SkillControlDTOs.FormDraftRequest request) {
        byte[] archive = zip(code, buildSkillMarkdown(code, request));
        return inspector.inspect(code + ".zip", archive);
    }

    private byte[] zip(String code, String markdown) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                ZipEntry directory = new ZipEntry(code + "/");
                directory.setTime(0L);
                zip.putNextEntry(directory);
                zip.closeEntry();
                ZipEntry skill = new ZipEntry(code + "/SKILL.md");
                skill.setTime(0L);
                zip.putNextEntry(skill);
                zip.write(markdown.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate Skill ZIP", ex);
        }
    }

    private String buildSkillMarkdown(String code, SkillControlDTOs.FormDraftRequest request) {
        StringBuilder value = new StringBuilder("---\n")
                .append("name: \"").append(yamlEscape(code)).append("\"\n")
                .append("description: \"").append(yamlEscape(description(request))).append("\"\n");
        if (StringUtils.hasText(request.getLicense())) {
            value.append("license: \"").append(yamlEscape(request.getLicense())).append("\"\n");
        }
        if (StringUtils.hasText(request.getCompatibility())) {
            value.append("compatibility: \"").append(yamlEscape(request.getCompatibility())).append("\"\n");
        }
        value.append("---\n\n").append(body(request).trim()).append('\n');
        return value.toString();
    }

    private String skillMarkdownBody(AiChatSkillVersionEntity version) {
        AiChatSkillFileEntity entry = listFiles(version.getId()).stream()
                .filter(file -> version.getEntrypoint().equalsIgnoreCase(file.getPath()))
                .findFirst().orElse(null);
        if (entry == null || entry.getContent() == null) return null;
        String markdown = new String(entry.getContent(), StandardCharsets.UTF_8);
        int first = markdown.indexOf('\n');
        int end = first < 0 ? -1 : markdown.indexOf("\n---", first + 1);
        if (end < 0) return markdown;
        int bodyStart = markdown.indexOf('\n', end + 1);
        return bodyStart < 0 ? "" : markdown.substring(bodyStart + 1).trim();
    }

    private List<AiChatSkillVersionEntity> versions(String code) {
        return versionMapper.selectList(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .orderByDesc(AiChatSkillVersionEntity::getVersionNo));
    }

    private List<AiChatSkillFileEntity> listFiles(Long versionId) {
        return fileMapper.selectList(Wrappers.<AiChatSkillFileEntity>lambdaQuery()
                .eq(AiChatSkillFileEntity::getSkillVersionId, versionId)
                .orderByAsc(AiChatSkillFileEntity::getPath));
    }

    private AiChatSkillPackageEntity findPackage(Long versionId) {
        return packageMapper.selectOne(Wrappers.<AiChatSkillPackageEntity>lambdaQuery()
                .eq(AiChatSkillPackageEntity::getSkillVersionId, versionId));
    }

    private AiChatSkillPackageEntity requirePackage(Long versionId) {
        AiChatSkillPackageEntity value = findPackage(versionId);
        if (value == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return value;
    }

    private void deleteVersionContent(Long versionId) {
        fileMapper.delete(Wrappers.<AiChatSkillFileEntity>lambdaQuery()
                .eq(AiChatSkillFileEntity::getSkillVersionId, versionId));
        packageMapper.delete(Wrappers.<AiChatSkillPackageEntity>lambdaQuery()
                .eq(AiChatSkillPackageEntity::getSkillVersionId, versionId));
    }

    private AiChatSkillVersionEntity requireVersion(String code, Integer version) {
        if (version == null || version < 1) throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        AiChatSkillVersionEntity entity = versionMapper.selectOne(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .eq(AiChatSkillVersionEntity::getVersionNo, version));
        if (entity == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return entity;
    }

    private AiChatSkillEntity requireCatalog(String code) {
        AiChatSkillEntity value = findCatalog(code);
        if (value == null) throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        return value;
    }

    private AiChatSkillEntity findCatalog(String code) {
        return skillMapper.selectOne(Wrappers.<AiChatSkillEntity>lambdaQuery()
                .eq(AiChatSkillEntity::getCode, code));
    }

    private AiChatSkillVersionEntity latest(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .orderByDesc(AiChatSkillVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatSkillVersionEntity latestMutable(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .ne(AiChatSkillVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .ne(AiChatSkillVersionEntity::getStatus, DefinitionStatus.ARCHIVED)
                .orderByDesc(AiChatSkillVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private AiChatSkillVersionEntity latestPublished(String code) {
        return versionMapper.selectOne(Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                .eq(AiChatSkillVersionEntity::getSkillCode, code)
                .eq(AiChatSkillVersionEntity::getStatus, DefinitionStatus.PUBLISHED)
                .orderByDesc(AiChatSkillVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private int nextVersion(String code) {
        AiChatSkillVersionEntity latest = latest(code);
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private String packageChecksum(List<InspectedSkillPackage.File> files) {
        String canonical = files.stream().sorted(java.util.Comparator.comparing(InspectedSkillPackage.File::getPath))
                .map(file -> file.getPath() + "\0" + file.getChecksum())
                .reduce("", (left, right) -> left + right + "\n");
        return json.sha256(canonical);
    }

    private String packageChecksumFromEntities(List<AiChatSkillFileEntity> files) {
        String canonical = files.stream().sorted(java.util.Comparator.comparing(AiChatSkillFileEntity::getPath))
                .map(file -> file.getPath() + "\0" + file.getChecksum())
                .reduce("", (left, right) -> left + right + "\n");
        return json.sha256(canonical);
    }

    private SkillControlDTOs.Catalog toCatalogDTO(AiChatSkillEntity entity) {
        SkillControlDTOs.Catalog dto = new SkillControlDTOs.Catalog();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDesc());
        dto.setEnabled(entity.getEnabled());
        AiChatSkillVersionEntity published = latestPublished(entity.getCode());
        AiChatSkillVersionEntity draft = latestMutable(entity.getCode());
        AiChatSkillVersionEntity current = draft != null ? draft : published;
        dto.setStatus(current == null || current.getStatus() == null ? null : current.getStatus().name());
        dto.setCurrentPublishedVersion(published == null ? null : published.getVersionNo());
        dto.setDraftVersion(draft == null ? null : draft.getVersionNo());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private void requireFormRequest(SkillControlDTOs.FormDraftRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())
                || !StringUtils.hasText(request.getName()) || !StringUtils.hasText(body(request))) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private String normalizeNewCode(String code) {
        if (!StringUtils.hasText(code) || code.length() > 64
                || !code.trim().matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim();
    }

    private String normalizePathCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9._-]{1,255}")) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        return code.trim();
    }

    private String body(SkillControlDTOs.FormDraftRequest request) {
        if (request == null) return null;
        return StringUtils.hasText(request.getContent()) ? request.getContent() : request.getInstructions();
    }

    private String description(SkillControlDTOs.FormDraftRequest request) {
        return StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : request.getName().trim();
    }

    private String fileRole(String path) {
        if (path == null) return "OTHER";
        String lower = path.toLowerCase(Locale.ROOT);
        if ("skill.md".equals(lower)) return "INSTRUCTIONS";
        if (lower.startsWith("scripts/")) return "SCRIPT";
        if (lower.startsWith("references/")) return "REFERENCE";
        if (lower.startsWith("assets/")) return "ASSET";
        if (lower.startsWith("templates/")) return "TEMPLATE";
        if (lower.startsWith("data/")) return "DATA";
        return "OTHER";
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (Object item : values) if (item != null) result.add(String.valueOf(item));
        return result;
    }

    private String text(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : String.valueOf(value).trim();
    }

    private String yamlEscape(String value) {
        return (value == null ? "" : value.trim()).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private String legacyPreview(String value) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() <= 255 ? text : text.substring(0, 252) + "...";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
