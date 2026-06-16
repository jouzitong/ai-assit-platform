package ai.platform.aiassit.db.engine.meta.enums;

public enum DbMetaImportJobStage {

    QUEUED,
    PARSING,
    IMPORTING_TABLES,
    IMPORTING_FIELDS,
    IMPORTING_INDEXES,
    FINALIZING,
    COMPLETED,
    FAILED
}
