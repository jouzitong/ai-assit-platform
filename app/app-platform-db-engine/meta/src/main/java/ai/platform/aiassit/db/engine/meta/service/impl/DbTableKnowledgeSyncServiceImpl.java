package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgePreviewDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgeSyncDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgeSyncItemDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableKnowledgeSyncRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableKnowledgePreviewService;
import ai.platform.aiassit.db.engine.meta.service.DbTableKnowledgeSyncService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseManageApi;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DbTableKnowledgeSyncServiceImpl implements DbTableKnowledgeSyncService {

    private static final String KB_ID_SETTING_KEY = "dbEngine.kb.kbId";
    private static final String SOURCE_SYSTEM = "dbEngine";

    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService fieldMetaService;
    private final DbTableKnowledgePreviewService knowledgePreviewService;
    private final AiKnowledgeBaseManageApi aiKnowledgeBaseManageApi;
    private final SystemSettingInternalApi systemSettingInternalApi;

    public DbTableKnowledgeSyncServiceImpl(DbTableMetaService tableMetaService,
                                           DbTableFieldMetaService fieldMetaService,
                                           DbTableKnowledgePreviewService knowledgePreviewService,
                                           AiKnowledgeBaseManageApi aiKnowledgeBaseManageApi,
                                           SystemSettingInternalApi systemSettingInternalApi) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.knowledgePreviewService = knowledgePreviewService;
        this.aiKnowledgeBaseManageApi = aiKnowledgeBaseManageApi;
        this.systemSettingInternalApi = systemSettingInternalApi;
    }

    @Override
    public DbTableKnowledgeSyncDTO sync(DbTableKnowledgeSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getSourceKey())) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }
        String sourceKey = request.getSourceKey().trim();
        String tableName = StringUtils.hasText(request.getTableName()) ? request.getTableName().trim() : null;
        String kbId = resolveKbId();

        List<DbTableMetaDTO> tables = loadTables(sourceKey, tableName);
        Map<String, List<DbTableFieldMetaDTO>> fieldsByTableName = loadFields(sourceKey, tableName);

        DbTableKnowledgeSyncDTO response = new DbTableKnowledgeSyncDTO();
        response.setKbId(kbId);
        response.setSourceKey(sourceKey);

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        List<DbTableKnowledgeSyncItemDTO> items = new ArrayList<>(tables.size());
        for (DbTableMetaDTO table : tables) {
            List<DbTableFieldMetaDTO> fields = fieldsByTableName.getOrDefault(table.getTableName(), List.of());
            DbTableKnowledgePreviewDTO preview = knowledgePreviewService.preview(table, fields);
            AiKbDocumentUpsertResponse upsertResponse = upsertDocument(kbId, sourceKey, table, preview);

            DbTableKnowledgeSyncItemDTO item = new DbTableKnowledgeSyncItemDTO();
            item.setTableName(table.getTableName());
            item.setDocumentId(buildDocumentId(sourceKey, table.getTableName()));
            item.setCreated(Boolean.TRUE.equals(upsertResponse.getCreated()));
            item.setUpdated(Boolean.TRUE.equals(upsertResponse.getUpdated()));
            item.setDraftVersionNo(upsertResponse.getDraftVersionNo());
            items.add(item);

            if (Boolean.TRUE.equals(upsertResponse.getCreated())) {
                createdCount++;
            } else if (Boolean.TRUE.equals(upsertResponse.getUpdated())) {
                updatedCount++;
            } else {
                unchangedCount++;
            }
        }

        response.setItems(items);
        response.setTotalCount(items.size());
        response.setCreatedCount(createdCount);
        response.setUpdatedCount(updatedCount);
        response.setUnchangedCount(unchangedCount);
        log.info("db table knowledge sync finish, sourceKey={}, tableName={}, kbId={}, total={}, created={}, updated={}, unchanged={}",
                sourceKey, tableName, kbId, items.size(), createdCount, updatedCount, unchangedCount);
        return response;
    }

    private String resolveKbId() {
        R<String> response = systemSettingInternalApi.queryValueByKey(KB_ID_SETTING_KEY);
        if (response == null || response.getCode() != 0 || !StringUtils.hasText(response.getData())) {
            throw new IllegalStateException("未配置系统参数 dbEngine.kb.kbId");
        }
        return response.getData().trim();
    }

    private List<DbTableMetaDTO> loadTables(String sourceKey, String tableName) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        List<DbTableMetaDTO> tables = tableMetaService.queryAll(query).stream()
                .filter(item -> StringUtils.hasText(item.getTableName()))
                .filter(item -> tableName == null || Objects.equals(tableName, item.getTableName()))
                .sorted(Comparator.comparing(DbTableMetaDTO::getTableName, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("未找到可同步的数据表元信息");
        }
        return tables;
    }

    private Map<String, List<DbTableFieldMetaDTO>> loadFields(String sourceKey, String tableName) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return fieldMetaService.queryAll(query).stream()
                .filter(item -> StringUtils.hasText(item.getTableName()))
                .filter(item -> tableName == null || Objects.equals(tableName, item.getTableName()))
                .sorted(Comparator
                        .comparing(DbTableFieldMetaDTO::getOrdinalPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DbTableFieldMetaDTO::getColumnName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.groupingBy(
                        DbTableFieldMetaDTO::getTableName,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private AiKbDocumentUpsertResponse upsertDocument(String kbId,
                                                      String sourceKey,
                                                      DbTableMetaDTO table,
                                                      DbTableKnowledgePreviewDTO preview) {
        AiKbDocumentUpsertRequest request = new AiKbDocumentUpsertRequest();
        request.setKbId(kbId);
        request.setDocumentId(buildDocumentId(sourceKey, table.getTableName()));
        request.setDocumentName(buildDocumentName(table));
        request.setDocumentType(AiKbDocumentType.DB_TABLE);
        request.setSourceKey(sourceKey);
        request.setContent(preview.getContent());
        request.setExt(buildExt(sourceKey, table));

        R<AiKbDocumentUpsertResponse> response = aiKnowledgeBaseManageApi.upsertDocument(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new IllegalStateException("知识库同步失败: " + table.getTableName());
        }
        return response.getData();
    }

    private Map<String, Object> buildExt(String sourceKey, DbTableMetaDTO table) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("sourceSystem", SOURCE_SYSTEM);
        ext.put("sourceKey", sourceKey);
        ext.put("tableName", table.getTableName());
        ext.put("tableComment", table.getTableComment());
        ext.put("partitionKey", table.getPartitionKey());
        return ext;
    }

    private String buildDocumentId(String sourceKey, String tableName) {
        return sourceKey + "/" + tableName;
    }

    private String buildDocumentName(DbTableMetaDTO table) {
        if (StringUtils.hasText(table.getTableComment())) {
            return table.getTableName() + " - " + table.getTableComment().trim();
        }
        return table.getTableName();
    }
}
