package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 工作流内部产物类型。
 */
@Getter
public enum AiChatArtifactType implements IEnum {
    INTENT_REWRITE(1, "IntentRewrite", "意图重写"),
    QUERY_PLAN(2, "QueryPlan", "查询计划"),
    CLARIFY_CANDIDATE(3, "ClarifyCandidate", "澄清候选"),
    KNOWLEDGE_QUERY(4, "KnowledgeQuery", "知识查询"),
    KNOWLEDGE_RESULT(5, "KnowledgeResult", "知识结果"),
    SKILL_CALL(6, "SkillCall", "技能调用"),
    SKILL_RESULT(7, "SkillResult", "技能结果"),
    SQL_DRAFT(8, "SqlDraft", "SQL草稿"),
    SQL_VALIDATION(9, "SqlValidation", "SQL验证"),
    SQL_VALIDATED(10, "SqlValidated", "SQL验证通过"),
    SQL_EXEC_RESULT(11, "SqlExecResult", "SQL执行结果"),
    MODEL_REQUEST_SNAPSHOT(12, "ModelRequestSnapshot", "模型请求快照"),
    MODEL_RESPONSE_SNAPSHOT(13, "ModelResponseSnapshot", "模型响应快照"),
    WORKFLOW_ERROR(14, "WorkflowError", "工作流错误"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiChatArtifactType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

}
