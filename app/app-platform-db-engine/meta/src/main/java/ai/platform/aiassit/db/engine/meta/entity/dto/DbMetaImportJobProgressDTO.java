package ai.platform.aiassit.db.engine.meta.entity.dto;

import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DbMetaImportJobProgressDTO {

    private String jobId;

    private String sourceKey;

    private String fileName;

    private DbMetaImportJobStatus status;

    private DbMetaImportJobStage stage;

    private Integer progressPercent;

    private String message;

    private List<String> recentMessages;

    private DbMetaImportProgressSummaryDTO summary;

    private DbMetaImportResultDTO result;
}
