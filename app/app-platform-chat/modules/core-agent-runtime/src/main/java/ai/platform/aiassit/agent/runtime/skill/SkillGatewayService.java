package ai.platform.aiassit.agent.runtime.skill;

import ai.platform.aiassit.agent.runtime.AgentCapabilityGrantService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResource;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResourceStore;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Read-only, run-scoped resource access preserving Skill progressive loading. */
@Service
public class SkillGatewayService {

    private static final int MAX_RESOURCE_BYTES = 25 * 1024 * 1024;
    private final PublishedSkillResourceStore resourceStore;
    private final AgentCapabilityGrantService grantService;

    public SkillGatewayService(PublishedSkillResourceStore resourceStore,
                               AgentCapabilityGrantService grantService) {
        this.resourceStore = resourceStore;
        this.grantService = grantService;
    }

    public SkillGatewayResponse read(String skillCode,
                                     Integer skillVersion,
                                     SkillGatewayRequest request,
                                     Long userId) {
        Map<String, Object> run = request == null || request.getRun() == null ? Map.of() : request.getRun();
        if (!grantService.allows(text(run.get("runId")), userId, text(run.get("snapshotHash")),
                "skill", skillCode, skillVersion)) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_PERMISSION_DENIED,
                    "Skill is not granted to this Agent run");
        }
        String path = request == null ? null : request.getPath();
        PublishedSkillResource resource = resourceStore.findPublished(skillCode, skillVersion, path)
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.TOOL_NOT_FOUND,
                        skillCode + "@" + skillVersion + ":" + path));
        byte[] content = resource.getContent();
        if (content.length > MAX_RESOURCE_BYTES) {
            throw BizException.of(AiChatBizCodeConstant.TOOL_INVOCATION_FAILED,
                    "Skill resource exceeds the runtime read limit");
        }
        String encoding = isUtf8(content) ? "utf-8" : "base64";
        String value = "utf-8".equals(encoding)
                ? new String(content, StandardCharsets.UTF_8)
                : Base64.getEncoder().encodeToString(content);
        return SkillGatewayResponse.builder()
                .skillCode(resource.getSkillCode())
                .skillVersion(resource.getSkillVersion())
                .path(resource.getPath())
                .mediaType(resource.getMediaType())
                .checksum(resource.getChecksum())
                .encoding(encoding)
                .content(value)
                .build();
    }

    private boolean isUtf8(byte[] value) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value));
            return true;
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private String text(Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
