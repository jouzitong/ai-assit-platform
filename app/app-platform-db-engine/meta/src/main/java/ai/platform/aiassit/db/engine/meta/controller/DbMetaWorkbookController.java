package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaExportFileDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.service.DbMetaImportJobService;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import jakarta.servlet.http.HttpServletResponse;
import org.arthena.framework.common.exception.BizException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 数据源元数据工作簿导入导出接口。
 *
 * <p>用于以工作簿方式迁移数据源、表、字段和关联等元数据；导出以附件响应返回，导入既可同步完成也可通过 SSE 观察长任务进度。</p>
 */
@RestController
@RequestMapping("/api/v1/meta/workbook")
public class DbMetaWorkbookController {

    private final DbMetaWorkbookService workbookService;
    private final DbMetaImportJobService importJobService;

    public DbMetaWorkbookController(DbMetaWorkbookService workbookService, DbMetaImportJobService importJobService) {
        this.workbookService = workbookService;
        this.importJobService = importJobService;
    }

    /**
     * 下载元数据工作簿模板。
     *
     * @param format   导出格式，默认 {@code json}
     * @param response HTTP 响应，用于写入带文件名和内容类型的模板附件
     * @return 无 JSON 响应体；工作簿二进制内容通过 HTTP 下载响应返回
     * @throws IOException 写入下载响应失败时抛出
     */
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

    /**
     * 导出指定数据源的完整元数据工作簿。
     *
     * @param sourceKey 数据源业务标识，决定导出的元数据范围
     * @param format    导出格式，默认 {@code json}
     * @param response  HTTP 响应，用于写入带文件名和内容类型的工作簿附件
     * @return 无 JSON 响应体；工作簿二进制内容通过 HTTP 下载响应返回
     * @throws IOException 写入下载响应失败时抛出
     */
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

    /**
     * 同步导入元数据工作簿。
     *
     * @param sourceKey 可选数据源标识；用于将工作簿内容导入或覆盖到指定数据源范围
     * @param file      待导入的工作簿文件，不能为空
     * @return 导入结果，包含成功、失败和校验明细
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DbMetaImportResultDTO importWorkbook(
            @RequestParam(required = false) String sourceKey,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_IMPORT_FILE);
        }
        return workbookService.importWorkbook(sourceKey, file);
    }

    /**
     * 异步导入元数据工作簿并通过 SSE 推送进度。
     *
     * @param sourceKey 可选数据源标识；用于限定导入范围
     * @param file      待导入的工作簿文件，不能为空
     * @return SSE 事件流，持续推送导入阶段、进度和最终结果
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping(value = "/import/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamImportWorkbook(
            @RequestParam(required = false) String sourceKey,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_IMPORT_FILE);
        }
        return importJobService.streamImport(sourceKey, file);
    }

}
