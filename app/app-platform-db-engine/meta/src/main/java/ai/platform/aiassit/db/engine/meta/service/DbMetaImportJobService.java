package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportJobCreateResponse;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportJobProgressDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DbMetaImportJobService {

    DbMetaImportJobCreateResponse createImportJob(String sourceKey, MultipartFile file) throws IOException;

    DbMetaImportJobProgressDTO getImportJobProgress(String jobId);
}
