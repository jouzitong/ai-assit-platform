package ai.platform.aiassit.db.engine.meta.controller;

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
    public void exportTemplateWorkbook(HttpServletResponse response) throws IOException {
        byte[] content = workbookService.exportTemplateWorkbook();
        String filename = URLEncoder.encode("db-meta-template.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @GetMapping("/export")
    public void exportWorkbook(@RequestParam String sourceKey, HttpServletResponse response) throws IOException {
        byte[] content = workbookService.exportWorkbook(sourceKey);
        String filename = URLEncoder.encode(buildFilename(sourceKey), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(content);
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

    private String buildFilename(String sourceKey) {
        String normalized = StringUtils.hasText(sourceKey) ? sourceKey : "all";
        return normalized + "-meta-workbook.xlsx";
    }
}
