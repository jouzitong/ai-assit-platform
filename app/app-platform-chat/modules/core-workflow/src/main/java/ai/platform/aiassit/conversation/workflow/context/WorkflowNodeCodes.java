package ai.platform.aiassit.conversation.workflow.context;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 工作流节点定义。
 *
 * <p>每个枚举项统一维护枚举编码、节点名称、节点编码及用途说明。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Getter
public enum WorkflowNodeCodes implements IEnum {

    CHAT_MESSAGE(1, "消息准备", "chat-message", "负责准备会话、轮次与当前用户消息上下文"),
    SIMPLE_CHAT(6, "简单对话", "simple-chat", "负责直接生成自然语言回答，不进入问数渲染链路"),
    QUERY_PLANNING(2, "查询规划", "query-planning", "负责识别用户查询主体、条件、意图与歧义"),
    KNOWLEDGE_SEARCH(3, "知识检索", "knowledge-search", "负责补充知识库背景、术语口径和业务规则"),
    SQL_PRE_GENERATE(4, "SQL 预生成", "sql-pre-generate", "负责基于规划和知识上下文生成预生成结果与伪 SQL"),
    RESULT_EVALUATE(5, "结果评估", "result-evaluate", "负责判断当前规划与 SQL 预生成结果是否满足进入渲染阶段的条件"),
    RENDER(7, "结果渲染", "render", "负责组织最终回答并落库助手消息");

    @JsonValue
    private final int code;

    private final String name;

    private final String nodeCode;

    private final String description;

    WorkflowNodeCodes(int code, String name, String nodeCode, String description) {
        this.code = code;
        this.name = name;
        this.nodeCode = nodeCode;
        this.description = description;
    }
}
