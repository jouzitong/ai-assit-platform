package ai.platform.aiassit.db.engine.meta.service.importer;

import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DbMetaImportService {

    boolean supports(MultipartFile file);

    String getFormat();

    DbMetaImportData parse(String sourceKey, MultipartFile file) throws IOException;
}
