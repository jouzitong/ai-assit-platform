package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.service.importer.DbMetaImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class DbMetaJsonImportServiceImpl implements DbMetaImportService {

    private final ObjectMapper objectMapper;

    public DbMetaJsonImportServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(filename);
        return "json".equalsIgnoreCase(extension);
    }

    @Override
    public String getFormat() {
        return "json";
    }

    @Override
    public DbMetaImportData parse(String sourceKey, MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            DbMetaImportData importData = objectMapper.readValue(inputStream, DbMetaImportData.class);
            if (importData.getTables() == null) {
                importData.setTables(new java.util.ArrayList<>());
            }
            if (importData.getFields() == null) {
                importData.setFields(new java.util.ArrayList<>());
            }
            if (importData.getIndexes() == null) {
                importData.setIndexes(new java.util.ArrayList<>());
            }
            return importData;
        }
    }
}
