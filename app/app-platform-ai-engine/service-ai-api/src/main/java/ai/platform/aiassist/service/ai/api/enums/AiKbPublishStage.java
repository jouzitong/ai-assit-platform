package ai.platform.aiassist.service.ai.api.enums;

public enum AiKbPublishStage {
    PREPARE_VERSION,
    VALIDATE_DOCUMENTS,
    GENERATE_DIFF,
    UPSERT_AI_DOCUMENTS,
    DELETE_AI_DOCUMENTS,
    FINALIZE,
    COMPLETED,
    FAILED
}
