package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaExportFileDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DbMetaWorkbookService {

    DbMetaExportFileDTO exportTemplateWorkbook(String format) throws IOException;

    DbMetaExportFileDTO exportWorkbook(String sourceKey, String format) throws IOException;

    DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException;
}
