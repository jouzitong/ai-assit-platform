package ai.platform.aiassit.db.engine.meta.controller;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaCascadeDeleteResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgePreviewDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgeSyncDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaCascadeDeleteRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableKnowledgeSyncRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaCascadeDeleteService;
import ai.platform.aiassit.db.engine.meta.service.DbTableKnowledgePreviewService;
import ai.platform.aiassit.db.engine.meta.service.DbTableKnowledgeSyncService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据表元数据及其知识库同步管理接口。
 *
 * <p>复用 {@link BaseController} 维护表目录，同时提供表的知识库描述预览、同步和关联元数据级联删除能力。</p>
 */
@RestController
@RequestMapping("/api/v1/meta/table")
public class DbTableMetaController
        extends BaseController<DbTableMetaDTO, DbTableMetaQueryRequest, DbTableMetaService> {

    private final DbTableMetaService service;
    private final DbTableMetaCascadeDeleteService cascadeDeleteService;
    private final DbTableKnowledgePreviewService knowledgePreviewService;
    private final DbTableKnowledgeSyncService knowledgeSyncService;

    public DbTableMetaController(
            DbTableMetaService service,
            DbTableMetaCascadeDeleteService cascadeDeleteService,
            DbTableKnowledgePreviewService knowledgePreviewService,
            DbTableKnowledgeSyncService knowledgeSyncService
    ) {
        this.service = service;
        this.cascadeDeleteService = cascadeDeleteService;
        this.knowledgePreviewService = knowledgePreviewService;
        this.knowledgeSyncService = knowledgeSyncService;
    }

    @Override
    protected DbTableMetaService service() {
        return service;
    }

    /**
     * 预览一张物理表生成的知识库描述内容。
     *
     * @param sourceKey 数据源业务标识
     * @param tableName 物理表名
     * @return 基于表、字段和关联元数据生成的知识库描述预览
     */
    @GetMapping("/knowledge-preview")
    public DbTableKnowledgePreviewDTO knowledgePreview(
            @RequestParam String sourceKey,
            @RequestParam String tableName
    ) {
        return knowledgePreviewService.preview(sourceKey, tableName);
    }

    /**
     * 将表元数据生成并同步到配置的知识库。
     *
     * @param request 同步请求体，包含数据源、表范围及同步选项
     * @return 同步结果，包含各表的生成与写入状态
     */
    @PostMapping("/knowledge-sync")
    public DbTableKnowledgeSyncDTO knowledgeSync(@RequestBody DbTableKnowledgeSyncRequest request) {
        return knowledgeSyncService.sync(request);
    }

    /**
     * 级联删除数据表元数据及其依赖的字段、索引和关联记录。
     *
     * @param request 级联删除请求体，包含待删除表和确认范围
     * @return 删除结果，包含各类关联记录的实际删除情况
     */
    @PostMapping("/delete-cascade")
    public DbTableMetaCascadeDeleteResultDTO deleteCascade(@RequestBody DbTableMetaCascadeDeleteRequest request) {
        return cascadeDeleteService.delete(request);
    }
}
