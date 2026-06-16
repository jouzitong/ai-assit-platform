package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;

public interface DbMetaImportProgressListener {

    DbMetaImportProgressListener NOOP = new DbMetaImportProgressListener() {
    };

    default void onStageChanged(DbMetaImportJobStage stage, String stageMessage) {
    }

    default void onTableProgress(int processed, int total, int createdCount, int updatedCount) {
    }

    default void onFieldProgress(int processed, int total, int createdCount, int updatedCount) {
    }

    default void onIndexProgress(int processed, int total, int createdCount, int updatedCount) {
    }
}
