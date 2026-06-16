package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DbMetaImportJobCreateResponse {

    private String jobId;
}
