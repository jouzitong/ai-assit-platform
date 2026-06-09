package ai.platform.aiassit.chat.history.enums;

/**
 * 工作流内部产物类型。
 */
public enum AiChatArtifactType {
    INTENT_REWRITE,
    QUERY_PLAN,
    CLARIFY_CANDIDATE,
    KNOWLEDGE_QUERY,
    KNOWLEDGE_RESULT,
    SKILL_CALL,
    SKILL_RESULT,
    SQL_DRAFT,
    SQL_VALIDATION,
    SQL_VALIDATED,
    SQL_EXEC_RESULT,
    MODEL_REQUEST_SNAPSHOT,
    MODEL_RESPONSE_SNAPSHOT,
    WORKFLOW_ERROR
}
