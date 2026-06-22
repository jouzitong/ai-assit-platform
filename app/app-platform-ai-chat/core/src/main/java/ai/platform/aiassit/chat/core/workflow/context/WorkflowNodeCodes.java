package ai.platform.aiassit.chat.core.workflow.context;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 工作流节点定义。
 *
 * <p>每个枚举项统一维护枚举编码、节点名称、节点编码、节点类型及用途说明。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Getter
public enum WorkflowNodeCodes implements IEnum {

    CHAT_MESSAGE(1, "消息准备", "chat-message", "Chat-Message", "负责准备会话、轮次与当前用户消息上下文"),
    QUERY_PLANNING(2, "查询规划", "query-planning", "Query-Planning", "负责识别用户查询主体、条件、意图与歧义"),
    KNOWLEDGE_SEARCH(3, "知识检索", "knowledge-search", "Knowledge-Search", "负责补充知识库背景、术语口径和业务规则"),
    SQL_GENERATE(4, "SQL 生成", "sql-generate", "Sql-Generate", "负责基于规划和知识上下文生成候选 SQL"),
    SQL_VALIDATE(5, "SQL 校验", "sql-validate", "Sql-Validate", "负责校验 SQL 安全性并给出回跳反馈"),
    SQL_EXECUTE(6, "SQL 执行", "sql-execute", "Sql-Execute", "负责执行或降级输出 SQL 执行结果"),
    RENDER(7, "结果渲染", "render", "Render", "负责组织最终回答并落库助手消息");

    @JsonValue
    private final int code;

    private final String name;

    private final String nodeCode;

    private final String nodeType;

    private final String description;

    WorkflowNodeCodes(int code, String name, String nodeCode, String nodeType, String description) {
        this.code = code;
        this.name = name;
        this.nodeCode = nodeCode;
        this.nodeType = nodeType;
        this.description = description;
    }
}
