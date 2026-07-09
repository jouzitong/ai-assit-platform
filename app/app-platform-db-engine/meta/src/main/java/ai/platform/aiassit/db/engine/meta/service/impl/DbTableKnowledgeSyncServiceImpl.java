package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
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
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
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
    private final AiKnowledgeApi aiKnowledgeApi;
    private final SystemSettingInternalApi systemSettingInternalApi;

    public DbTableKnowledgeSyncServiceImpl(DbTableMetaService tableMetaService,
                                           DbTableFieldMetaService fieldMetaService,
                                           DbTableKnowledgePreviewService knowledgePreviewService,
                                           AiKnowledgeApi aiKnowledgeApi,
                                           SystemSettingInternalApi systemSettingInternalApi) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.knowledgePreviewService = knowledgePreviewService;
        this.aiKnowledgeApi = aiKnowledgeApi;
        this.systemSettingInternalApi = systemSettingInternalApi;
    }

    @Override
    public DbTableKnowledgeSyncDTO sync(DbTableKnowledgeSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getSourceKey())) {
            throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_SOURCE_KEY);
        }
        String sourceKey = request.getSourceKey().trim();
        String tableName = StringUtils.hasText(request.getTableName()) ? request.getTableName().trim() : null;
        log.info("start db table knowledge sync, sourceKey={}, tableName={}, mode={}",
                sourceKey, tableName, tableName == null ? "ALL_TABLES" : "SINGLE_TABLE");
        String kbId = resolveKbId();
        log.info("resolved knowledge base id for db table sync, sourceKey={}, kbId={}", sourceKey, kbId);

        List<DbTableMetaDTO> tables = loadTables(sourceKey, tableName);
        Map<String, List<DbTableFieldMetaDTO>> fieldsByTableName = loadFields(sourceKey, tableName);
        log.info("loaded db metadata for knowledge sync, sourceKey={}, tableName={}, tableCount={}, fieldTableCount={}",
                sourceKey, tableName, tables.size(), fieldsByTableName.size());

        DbTableKnowledgeSyncDTO response = new DbTableKnowledgeSyncDTO();
        response.setKbId(kbId);
        response.setSourceKey(sourceKey);

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        int total = tables.size();
        List<DbTableKnowledgeSyncItemDTO> items = new ArrayList<>(tables.size());
        for (int i = 0; i < total; i++) {
            DbTableMetaDTO table = tables.get(i);
            String currentTableName = table.getTableName();
            List<DbTableFieldMetaDTO> fields = fieldsByTableName.getOrDefault(currentTableName, List.of());
            log.info("sync progress {}/{}, sourceKey={}, tableName={}, fieldCount={}",
                    i + 1, total, sourceKey, currentTableName, fields.size());
            DbTableKnowledgePreviewDTO preview;
            AiKbDocumentUpsertResponse upsertResponse;
            try {
                preview = knowledgePreviewService.preview(table, fields);
                log.debug("generated knowledge preview for table sync, sourceKey={}, tableName={}, contentLength={}",
                        sourceKey, currentTableName, preview.getContent() == null ? 0 : preview.getContent().length());
                upsertResponse = upsertDocument(kbId, sourceKey, table, preview);
            } catch (Exception ex) {
                log.error("db table knowledge sync failed at progress {}/{}, sourceKey={}, tableName={}",
                        i + 1, total, sourceKey, currentTableName, ex);
                throw ex;
            }

            DbTableKnowledgeSyncItemDTO item = new DbTableKnowledgeSyncItemDTO();
            item.setTableName(currentTableName);
            item.setDocumentId(buildDocumentId(sourceKey, currentTableName));
            item.setCreated(Boolean.TRUE.equals(upsertResponse.getCreated()));
            item.setUpdated(Boolean.TRUE.equals(upsertResponse.getUpdated()));
            item.setCurrentVersionNo(upsertResponse.getCurrentVersionNo());
            items.add(item);

            if (Boolean.TRUE.equals(upsertResponse.getCreated())) {
                createdCount++;
            } else if (Boolean.TRUE.equals(upsertResponse.getUpdated())) {
                updatedCount++;
            } else {
                unchangedCount++;
            }
            log.info("finished table sync {}/{}, sourceKey={}, tableName={}, created={}, updated={}, currentVersionNo={}",
                    i + 1, total, sourceKey, currentTableName, item.getCreated(), item.getUpdated(), item.getCurrentVersionNo());
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
            log.error("failed to resolve knowledge base id, settingKey={}, responseCode={}, hasData={}",
                    KB_ID_SETTING_KEY,
                    response == null ? null : response.getCode(),
                    response != null && StringUtils.hasText(response.getData()));
            throw BizException.of(DbEngineBizCodeConstant.KB_ID_SETTING_MISSING, KB_ID_SETTING_KEY);
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
            log.warn("no db table metadata found for knowledge sync, sourceKey={}, tableName={}", sourceKey, tableName);
            throw BizException.of(DbEngineBizCodeConstant.SYNC_TABLE_META_NOT_FOUND);
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
        request.setBizType(AiKbBizType.DB_DATA_SOURCE);
        request.setContent(preview.getContent());
        request.setExt(buildExt(sourceKey, table));

        log.debug("upserting knowledge document, kbId={}, sourceKey={}, tableName={}, documentId={}, documentName={}",
                kbId, sourceKey, table.getTableName(), request.getDocumentId(), request.getDocumentName());
        R<AiKbDocumentUpsertResponse> response = aiKnowledgeApi.upsertDocument(request);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            log.error("knowledge document upsert failed, kbId={}, sourceKey={}, tableName={}, responseCode={}, hasData={}",
                    kbId,
                    sourceKey,
                    table.getTableName(),
                    response == null ? null : response.getCode(),
                    response != null && response.getData() != null);
            throw BizException.of(DbEngineBizCodeConstant.KB_SYNC_FAILED, table.getTableName());
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
