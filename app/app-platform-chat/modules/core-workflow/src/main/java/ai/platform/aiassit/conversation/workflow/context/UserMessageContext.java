package ai.platform.aiassit.conversation.workflow.context;

import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息上下文。
 *
 * <p>用于统一管理当前用户输入、历史用户消息以及面向后续节点消费的汇总说明。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class UserMessageContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前轮次的用户输入消息。
     */
    private AiChatMessageDTO currentMessage;

    /**
     * 当前会话消息列表，默认不包含当前轮次新落库的用户输入；后续流程新增消息也会追加到这里。
     */
    private List<AiChatMessageDTO> sessionMessages = new ArrayList<>();

    /**
     * 对当前输入和历史输入整理后的汇总说明。
     */
    private String summary;

    /**
     * 已失效历史意图列表。
     */
    private List<InvalidIntentItem> invalidIntents = new ArrayList<>();
}
