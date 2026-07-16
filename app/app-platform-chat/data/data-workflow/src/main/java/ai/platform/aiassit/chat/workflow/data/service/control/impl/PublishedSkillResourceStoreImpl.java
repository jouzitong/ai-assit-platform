package ai.platform.aiassit.chat.workflow.data.service.control.impl;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillFileEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillVersionEntity;
import ai.platform.aiassit.chat.workflow.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillFileMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillVersionMapper;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResource;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResourceStore;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class PublishedSkillResourceStoreImpl implements PublishedSkillResourceStore {

    private final AiChatSkillVersionMapper versionMapper;
    private final AiChatSkillFileMapper fileMapper;

    public PublishedSkillResourceStoreImpl(AiChatSkillVersionMapper versionMapper,
                                           AiChatSkillFileMapper fileMapper) {
        this.versionMapper = versionMapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public Optional<PublishedSkillResource> findPublished(String skillCode,
                                                           Integer skillVersion,
                                                           String path) {
        String normalizedPath = normalizePath(path);
        if (!StringUtils.hasText(skillCode) || skillVersion == null || skillVersion < 1
                || !skillCode.trim().matches("[A-Za-z0-9._-]{1,255}") || normalizedPath == null) {
            return Optional.empty();
        }
        AiChatSkillVersionEntity version = versionMapper.selectOne(
                Wrappers.<AiChatSkillVersionEntity>lambdaQuery()
                        .eq(AiChatSkillVersionEntity::getSkillCode, skillCode.trim())
                        .eq(AiChatSkillVersionEntity::getVersionNo, skillVersion)
                        .eq(AiChatSkillVersionEntity::getStatus, DefinitionStatus.PUBLISHED));
        if (version == null) return Optional.empty();
        AiChatSkillFileEntity file = fileMapper.selectOne(
                Wrappers.<AiChatSkillFileEntity>lambdaQuery()
                        .eq(AiChatSkillFileEntity::getSkillVersionId, version.getId())
                        .eq(AiChatSkillFileEntity::getPath, normalizedPath));
        if (file == null || file.getContent() == null) return Optional.empty();
        return Optional.of(PublishedSkillResource.builder()
                .skillCode(version.getSkillCode())
                .skillVersion(version.getVersionNo())
                .path(file.getPath())
                .mediaType(file.getMediaType())
                .checksum(file.getChecksum())
                .content(file.getContent())
                .build());
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value) || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0) return null;
        String path = value.trim();
        if (path.startsWith("/") || path.length() > 512) return null;
        String[] parts = path.split("/", -1);
        for (String part : parts) if (part.isBlank() || ".".equals(part) || "..".equals(part)) return null;
        return String.join("/", parts);
    }
}
