package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 工作流内部产物类型。
 */
@Getter
public enum AiChatArtifactType implements IEnum {
    INTENT_REWRITE(1, "意图重写"),
    QUERY_PLAN(2, "查询计划"),
    CLARIFY_CANDIDATE(3, "澄清候选"),
    KNOWLEDGE_QUERY(4, "知识查询"),
    KNOWLEDGE_RESULT(5, "知识结果"),
    SKILL_CALL(6, "技能调用"),
    SKILL_RESULT(7, "技能结果"),
    SQL_DRAFT(8, "SQL草稿"),
    SQL_VALIDATION(9, "SQL验证"),
    SQL_VALIDATED(10, "SQL验证通过"),
    SQL_EXEC_RESULT(11, "SQL执行结果"),
    MODEL_REQUEST_SNAPSHOT(12, "模型请求快照"),
    MODEL_RESPONSE_SNAPSHOT(13, "模型响应快照"),
    WORKFLOW_ERROR(14, "工作流错误"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatArtifactType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
