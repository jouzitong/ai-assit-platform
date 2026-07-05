package ai.platform.aiassit.conversation.workflow.planning.contract;

import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import lombok.Data;

/**
 * 查询规划上下文消息。
 *
 * <p>由 planning skill 直接生成，供 QueryPlanningNode 组装为模型请求消息。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class PlanningContextMessage {

    /**
     * 消息来源 skill。
     */
    private String source;

    /**
     * 消息片段标题。
     */
    private String section;

    /**
     * 发给模型的消息角色。
     */
    private MessageRole role = MessageRole.SYSTEM;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 拼接优先级，数值越小越靠前。
     */
    private Integer priority = 100;
}
