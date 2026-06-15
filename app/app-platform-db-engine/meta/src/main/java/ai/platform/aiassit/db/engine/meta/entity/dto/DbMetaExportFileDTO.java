package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DbMetaExportFileDTO {

    private byte[] content;

    private String filename;

    private String contentType;
}
