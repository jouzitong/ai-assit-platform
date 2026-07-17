package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationRoundDetailVO {

    private ConversationRoundDTO round;

    private List<ConversationMessageDTO> messages = new ArrayList<>();

    private List<ConversationArtifactDTO> artifacts = new ArrayList<>();

    /** Persisted Agent/tool/handoff/check activity timeline for history replay. */
    private List<ConversationActivityDTO> activities = new ArrayList<>();

    /**
     * TODO: 后续在这里补充 artifact -> renderer 的聚合结果，
     * 由前端基于该标识决定是否进入业务画布渲染模式。
     */
    private String renderType;
}
