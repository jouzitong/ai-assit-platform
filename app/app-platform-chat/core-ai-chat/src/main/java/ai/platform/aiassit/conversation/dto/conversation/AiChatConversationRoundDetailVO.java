package ai.platform.aiassit.conversation.dto.conversation;

import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatConversationRoundDetailVO {

    private AiChatRoundDTO round;

    private List<AiChatMessageDTO> messages = new ArrayList<>();

    private List<AiChatArtifactDTO> artifacts = new ArrayList<>();

    /**
     * TODO: 后续在这里补充 artifact -> renderer 的聚合结果，
     * 由前端基于该标识决定是否进入业务画布渲染模式。
     */
    private String renderType;
}
