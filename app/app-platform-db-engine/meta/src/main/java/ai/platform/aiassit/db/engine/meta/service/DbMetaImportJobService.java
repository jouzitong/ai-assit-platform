package ai.platform.aiassit.db.engine.meta.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public interface DbMetaImportJobService {

    SseEmitter streamImport(String sourceKey, MultipartFile file) throws IOException;
}
