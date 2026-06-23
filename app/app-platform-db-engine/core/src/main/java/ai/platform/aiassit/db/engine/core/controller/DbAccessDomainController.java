package ai.platform.aiassit.db.engine.core.controller;

import ai.platform.aiassit.db.engine.core.controller.req.DbAccessTableListRequest;
import ai.platform.aiassit.db.engine.core.controller.req.DbAccessTableSyncRequest;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableListResponse;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableRemoteItem;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableSyncItem;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableSyncResponse;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableMeta;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@RestController
@RequestMapping("/api/v1/db/access")
public class DbAccessDomainController {

    private final DbAccessService dbAccessService;
    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService tableFieldMetaService;

    public DbAccessDomainController(
            DbAccessService dbAccessService,
            DbTableMetaService tableMetaService,
            DbTableFieldMetaService tableFieldMetaService
    ) {
        this.dbAccessService = dbAccessService;
        this.tableMetaService = tableMetaService;
        this.tableFieldMetaService = tableFieldMetaService;
    }

    @PostMapping("/tables")
    public R<DbAccessTableListResponse> tables(@RequestBody DbAccessTableListRequest request) {
        String sourceKey = requireSourceKey(request == null ? null : request.getSourceKey());
        List<DbTableMeta> remoteTables = dbAccessService.listTables(
                        sourceKey,
                        ListTablesRequest.builder().limit(Integer.MAX_VALUE).build()
                ).getTables();
        Set<String> filterTables = normalizeNames(request == null ? null : request.getTables());
        Map<String, DbTableMetaDTO> localTableMap = loadLocalTables(sourceKey);

        List<DbAccessTableRemoteItem> items = new ArrayList<>();
        for (DbTableMeta remoteTable : remoteTables) {
            if (remoteTable == null || !containsOrAll(filterTables, remoteTable.getTableName())) {
                continue;
            }
            DbTableMetaDTO localTable = localTableMap.get(normalizeKey(remoteTable.getTableName()));
            DbAccessTableRemoteItem item = new DbAccessTableRemoteItem();
            item.setTableName(remoteTable.getTableName());
            item.setTableComment(remoteTable.getTableComment());
            item.setTableType(remoteTable.getTableType());
            item.setSynced(localTable != null);
            item.setTableMeta(localTable);
            items.add(item);
        }

        DbAccessTableListResponse response = new DbAccessTableListResponse();
        response.setSourceKey(sourceKey);
        response.setTables(items);
        return R.ok(response);
    }

    @PostMapping("/sync/table-meta")
    public R<DbAccessTableSyncResponse> syncTableMeta(@RequestBody DbAccessTableSyncRequest request) {
        String sourceKey = requireSourceKey(request == null ? null : request.getSourceKey());
        boolean allowUpdate = Boolean.TRUE.equals(request == null ? null : request.getAllowUpdate());
        Set<String> filterTables = normalizeNames(request == null ? null : request.getTables());
        Map<String, DbTableMetaDTO> localTableMap = loadLocalTables(sourceKey);
        Map<String, Map<String, DbTableFieldMetaDTO>> localFieldMap = loadLocalFields(sourceKey);

        List<DbTableMeta> remoteTables = dbAccessService.listTables(
                        sourceKey,
                        ListTablesRequest.builder().limit(Integer.MAX_VALUE).build()
                ).getTables();
        List<DbAccessTableSyncItem> items = new ArrayList<>();

        int createdTables = 0;
        int updatedTables = 0;
        int createdFields = 0;
        int updatedFields = 0;

        for (DbTableMeta remoteTable : remoteTables) {
            if (remoteTable == null || !containsOrAll(filterTables, remoteTable.getTableName())) {
                continue;
            }
            String tableKey = normalizeKey(remoteTable.getTableName());
            DbTableMetaDTO existingTable = localTableMap.get(tableKey);
            List<DbColumnMeta> remoteFields = dbAccessService.listTableColumns(
                            sourceKey,
                            ListTableColumnsRequest.builder().tableName(remoteTable.getTableName()).build()
                    ).getColumns();

            boolean tableCreated = false;
            boolean tableUpdated = false;
            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;

            if (existingTable == null) {
                DbTableMetaDTO created = tableMetaService.add(buildTableMeta(sourceKey, remoteTable, remoteFields, null));
                localTableMap.put(tableKey, created);
                existingTable = created;
                tableCreated = true;
                createdTables++;
            } else if (allowUpdate) {
                existingTable = tableMetaService.update(existingTable.getId(), buildTableMeta(sourceKey, remoteTable, remoteFields, existingTable));
                localTableMap.put(tableKey, existingTable);
                tableUpdated = true;
                updatedTables++;
            }

            Map<String, DbTableFieldMetaDTO> tableFieldMap = localFieldMap.computeIfAbsent(tableKey, key -> new LinkedHashMap<>());
            for (DbColumnMeta remoteField : remoteFields) {
                if (remoteField == null) {
                    continue;
                }
                String fieldKey = normalizeKey(remoteField.getColumnName());
                DbTableFieldMetaDTO existingField = tableFieldMap.get(fieldKey);
                if (existingField == null) {
                    DbTableFieldMetaDTO createdField = tableFieldMetaService.add(buildFieldMeta(sourceKey, remoteField, null));
                    tableFieldMap.put(fieldKey, createdField);
                    fieldCreatedCount++;
                    createdFields++;
                } else if (allowUpdate) {
                    DbTableFieldMetaDTO updatedField = tableFieldMetaService.update(existingField.getId(), buildFieldMeta(sourceKey, remoteField, existingField));
                    tableFieldMap.put(fieldKey, updatedField);
                    fieldUpdatedCount++;
                    updatedFields++;
                }
            }

            DbAccessTableSyncItem item = new DbAccessTableSyncItem();
            item.setTableName(remoteTable.getTableName());
            item.setTableCreated(tableCreated);
            item.setTableUpdated(tableUpdated);
            item.setFieldCreatedCount(fieldCreatedCount);
            item.setFieldUpdatedCount(fieldUpdatedCount);
            item.setRemoteFieldCount(remoteFields == null ? 0 : remoteFields.size());
            items.add(item);
        }

        DbAccessTableSyncResponse response = new DbAccessTableSyncResponse();
        response.setSourceKey(sourceKey);
        response.setAllowUpdate(allowUpdate);
        response.setCreatedTableCount(createdTables);
        response.setUpdatedTableCount(updatedTables);
        response.setCreatedFieldCount(createdFields);
        response.setUpdatedFieldCount(updatedFields);
        response.setItems(items);
        return R.ok(response);
    }

    private Map<String, DbTableMetaDTO> loadLocalTables(String sourceKey) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        Map<String, DbTableMetaDTO> result = new LinkedHashMap<>();
        for (DbTableMetaDTO item : tableMetaService.queryAll(query)) {
            result.put(normalizeKey(item.getTableName()), item);
        }
        return result;
    }

    private Map<String, Map<String, DbTableFieldMetaDTO>> loadLocalFields(String sourceKey) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        Map<String, Map<String, DbTableFieldMetaDTO>> result = new LinkedHashMap<>();
        for (DbTableFieldMetaDTO item : tableFieldMetaService.queryAll(query)) {
            result.computeIfAbsent(normalizeKey(item.getTableName()), key -> new LinkedHashMap<>())
                    .put(normalizeKey(item.getColumnName()), item);
        }
        return result;
    }

    private DbTableMetaDTO buildTableMeta(
            String sourceKey,
            DbTableMeta remoteTable,
            List<DbColumnMeta> remoteFields,
            DbTableMetaDTO existing
    ) {
        DbTableMetaDTO dto = existing == null ? new DbTableMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
        dto.setTableName(remoteTable.getTableName());
        dto.setTableComment(remoteTable.getTableComment());
        dto.setTableType(remoteTable.getTableType());
        dto.setColumnCount(remoteFields == null ? 0 : remoteFields.size());
        dto.setEnabled(existing == null ? Boolean.TRUE : existing.getEnabled());
        dto.setLastSyncAt(LocalDateTime.now());
        return dto;
    }

    private DbTableFieldMetaDTO buildFieldMeta(String sourceKey, DbColumnMeta remoteField, DbTableFieldMetaDTO existing) {
        DbTableFieldMetaDTO dto = existing == null ? new DbTableFieldMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
        dto.setTableName(remoteField.getTableName());
        dto.setColumnName(remoteField.getColumnName());
        dto.setColumnComment(remoteField.getColumnComment());
        dto.setDataType(remoteField.getDataType());
        dto.setColumnLength(remoteField.getColumnLength());
        dto.setColumnPrecision(remoteField.getColumnPrecision());
        dto.setColumnScale(remoteField.getColumnScale());
        dto.setNullable(remoteField.getNullable());
        dto.setPrimaryKey(remoteField.getPrimaryKey());
        dto.setDefaultValue(remoteField.getDefaultValue());
        dto.setOrdinalPosition(remoteField.getOrdinalPosition());
        dto.setEnabled(existing == null ? Boolean.TRUE : existing.getEnabled());
        return dto;
    }

    private String requireSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw BizException.of();
        }
        return sourceKey.trim();
    }

    private Set<String> normalizeNames(Collection<String> names) {
        Set<String> result = new LinkedHashSet<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                result.add(normalizeKey(name));
            }
        }
        return result;
    }

    private boolean containsOrAll(Set<String> filterTables, String tableName) {
        return filterTables.isEmpty() || filterTables.contains(normalizeKey(tableName));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
