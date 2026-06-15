package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaExportFileDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/meta/workbook")
public class DbMetaWorkbookController {

    private final DbMetaWorkbookService workbookService;

    public DbMetaWorkbookController(DbMetaWorkbookService workbookService) {
        this.workbookService = workbookService;
    }

    @GetMapping("/template")
    public void exportTemplateWorkbook(
            @RequestParam(required = false, defaultValue = "json") String format,
            HttpServletResponse response
    ) throws IOException {
        DbMetaExportFileDTO exportFile = workbookService.exportTemplateWorkbook(format);
        String filename = URLEncoder.encode(exportFile.getFilename(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType(exportFile.getContentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(exportFile.getContent());
        response.flushBuffer();
    }

    @GetMapping("/export")
    public void exportWorkbook(
            @RequestParam String sourceKey,
            @RequestParam(required = false, defaultValue = "json") String format,
            HttpServletResponse response
    ) throws IOException {
        DbMetaExportFileDTO exportFile = workbookService.exportWorkbook(sourceKey, format);
        String filename = URLEncoder.encode(exportFile.getFilename(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType(exportFile.getContentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(exportFile.getContent());
        response.flushBuffer();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DbMetaImportResultDTO importWorkbook(
            @RequestParam(required = false) String sourceKey,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        return workbookService.importWorkbook(sourceKey, file);
    }

}
