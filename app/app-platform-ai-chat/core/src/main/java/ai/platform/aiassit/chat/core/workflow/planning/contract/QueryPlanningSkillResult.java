package ai.platform.aiassit.chat.core.workflow.planning.contract;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询规划技能输出结果。
 *
 * <p>同时包含结构化证据和可直接拼接到模型请求中的上下文消息。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class QueryPlanningSkillResult {

    /**
     * 技能产生的结构化证据。
     */
    private IntentEvidence evidence;

    /**
     * 技能产生的上下文消息片段。
     */
    private List<PlanningContextMessage> messages = new ArrayList<>();
}
