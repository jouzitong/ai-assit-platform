package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DbMetaWorkbookService {

    byte[] exportTemplateWorkbook() throws IOException;

    byte[] exportWorkbook(String sourceKey) throws IOException;

    DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException;
}
