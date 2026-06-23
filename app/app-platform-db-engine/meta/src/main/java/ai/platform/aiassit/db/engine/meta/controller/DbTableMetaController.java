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

    @GetMapping("/knowledge-preview")
    public DbTableKnowledgePreviewDTO knowledgePreview(
            @RequestParam String sourceKey,
            @RequestParam String tableName
    ) {
        return knowledgePreviewService.preview(sourceKey, tableName);
    }

    @PostMapping("/knowledge-sync")
    public DbTableKnowledgeSyncDTO knowledgeSync(@RequestBody DbTableKnowledgeSyncRequest request) {
        return knowledgeSyncService.sync(request);
    }

    @PostMapping("/delete-cascade")
    public DbTableMetaCascadeDeleteResultDTO deleteCascade(@RequestBody DbTableMetaCascadeDeleteRequest request) {
        return cascadeDeleteService.delete(request);
    }
}
