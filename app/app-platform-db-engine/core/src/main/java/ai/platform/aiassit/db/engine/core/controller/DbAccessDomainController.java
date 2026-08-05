package ai.platform.aiassit.db.engine.core.controller;

import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import ai.platform.aiassit.db.engine.core.controller.req.DbAccessTableListRequest;
import ai.platform.aiassit.db.engine.core.controller.req.DbAccessTableDataPreviewRequest;
import ai.platform.aiassit.db.engine.core.controller.req.DbAccessTableSyncRequest;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableListResponse;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableDataPreviewResponse;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableRemoteItem;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableSyncItem;
import ai.platform.aiassit.db.engine.core.controller.resp.DbAccessTableSyncResponse;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.core.service.DataAccessService;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbIndexMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableMeta;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableIndexesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
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
 * 物理数据源发现、受控预览与元数据同步接口。
 *
 * <p>接口可测试连接并读取物理库结构，但数据预览只允许已登记表且不接收任意 SQL 或过滤条件；同步仅刷新数据库结构信息，保留本地人工配置。</p>
 */
@RestController
@RequestMapping("/api/v1/db/access")
public class DbAccessDomainController {

    private final DbAccessService dbAccessService;
    private final DataAccessService dataAccessService;
    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService tableFieldMetaService;
    private final DbTableIndexMetaService tableIndexMetaService;

    public DbAccessDomainController(
            DbAccessService dbAccessService,
            DataAccessService dataAccessService,
            DbTableMetaService tableMetaService,
            DbTableFieldMetaService tableFieldMetaService,
            DbTableIndexMetaService tableIndexMetaService
    ) {
        this.dbAccessService = dbAccessService;
        this.dataAccessService = dataAccessService;
        this.tableMetaService = tableMetaService;
        this.tableFieldMetaService = tableFieldMetaService;
        this.tableIndexMetaService = tableIndexMetaService;
    }

    /**
     * 测试候选数据源的连通性。
     *
     * @param request 数据源配置请求体，包含连接地址、类型和认证信息
     * @return 连通性测试结果，包含是否可连接及失败诊断
     */
    @PostMapping("/test-connection")
    public R<TestConnectionResult> testConnection(@RequestBody DbDataSourceDTO request) {
        return R.ok(dbAccessService.testConnection(request));
    }

    /**
     * 拉取物理数据源中的表，并标识其是否已登记为本地元数据。
     *
     * @param request 表列表请求体，包含数据源标识和可选表名筛选集合
     * @return 物理表列表及每张表对应的本地登记状态和元数据摘要
     */
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

    /**
     * 分页只读预览已登记数据表的样本记录。
     *
     * <p>请求不接受 SQL 或过滤条件，避免管理页面能力越界为任意查询入口；服务端额外读取一条记录以计算是否还有下一页，额外记录不会返回。</p>
     *
     * @param request 预览请求体，包含数据源、已登记表名和分页参数
     * @return 表数据预览，包含列定义、当前页记录、执行耗时和下一页标识
     */
    @PostMapping("/table-data-preview")
    public R<DbAccessTableDataPreviewResponse> tableDataPreview(
            @RequestBody DbAccessTableDataPreviewRequest request
    ) {
        String sourceKey = requireSourceKey(request == null ? null : request.getSourceKey());
        String tableName = requireTableName(request == null ? null : request.getTableName());
        requireRegisteredTable(sourceKey, tableName);

        int page = normalizePreviewPage(request == null ? null : request.getPage());
        int pageSize = normalizePreviewPageSize(request == null ? null : request.getPageSize());
        DataReadResult readResult = dataAccessService.read(sourceKey, DataReadCommand.builder()
                .resource(tableName)
                .page(page)
                // 多读取一条，仅用于判断是否存在下一页，最终不会返回给页面。
                .pageSize(pageSize + 1)
                .build());
        List<Map<String, Object>> fetchedRecords = readResult == null || readResult.getRecords() == null
                ? List.of()
                : readResult.getRecords();
        boolean hasNext = fetchedRecords.size() > pageSize;
        List<Map<String, Object>> records = new ArrayList<>(fetchedRecords.subList(0, Math.min(pageSize, fetchedRecords.size())));

        DbAccessTableDataPreviewResponse response = new DbAccessTableDataPreviewResponse();
        response.setSourceKey(sourceKey);
        response.setTableName(tableName);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setHasNext(hasNext);
        response.setRecords(records);
        response.setMetadata(readResult == null || readResult.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(readResult.getMetadata()));
        Object executionMs = response.getMetadata().get("executionMs");
        if (executionMs instanceof Number number) {
            response.setExecutionMs(number.longValue());
        }
        response.setColumns(resolvePreviewColumns(records));
        return R.ok(response);
    }

    /**
     * 将物理库的表、字段和索引结构同步到本地元数据目录。
     *
     * <p>同步范围可限制为指定表；本地的启用状态、分层和备注等人工配置会被保留，仅更新物理结构派生字段。</p>
     *
     * @param request 同步请求体，包含数据源标识和可选表名集合
     * @return 同步汇总结果，包含新增/更新计数和各表的明细
     */
    @PostMapping("/sync/table-meta")
    public R<DbAccessTableSyncResponse> syncTableMeta(@RequestBody DbAccessTableSyncRequest request) {
        String sourceKey = requireSourceKey(request == null ? null : request.getSourceKey());
        Set<String> filterTables = normalizeNames(request == null ? null : request.getTables());
        Map<String, DbTableMetaDTO> localTableMap = loadLocalTables(sourceKey);
        Map<String, Map<String, DbTableFieldMetaDTO>> localFieldMap = loadLocalFields(sourceKey);
        Map<String, Map<String, DbTableIndexMetaDTO>> localIndexMap = loadLocalIndexes(sourceKey);

        List<DbTableMeta> remoteTables = dbAccessService.listTables(
                        sourceKey,
                        ListTablesRequest.builder().limit(Integer.MAX_VALUE).build()
                ).getTables();
        List<DbAccessTableSyncItem> items = new ArrayList<>();

        int createdTables = 0;
        int updatedTables = 0;
        int createdFields = 0;
        int updatedFields = 0;
        int createdIndexes = 0;
        int updatedIndexes = 0;

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
            List<DbIndexMeta> remoteIndexes = dbAccessService.listTableIndexes(
                            sourceKey,
                            ListTableIndexesRequest.builder().tableName(remoteTable.getTableName()).build()
                    ).getIndexes();

            boolean tableCreated = false;
            boolean tableUpdated = false;
            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;
            int indexCreatedCount = 0;
            int indexUpdatedCount = 0;

            if (existingTable == null) {
                DbTableMetaDTO created = tableMetaService.add(buildTableMeta(sourceKey, remoteTable, remoteFields, null));
                localTableMap.put(tableKey, created);
                existingTable = created;
                tableCreated = true;
                createdTables++;
            } else if (hasPersistentId(existingTable.getId())) {
                existingTable = tableMetaService.update(existingTable.getId(), buildTableMeta(sourceKey, remoteTable, remoteFields, existingTable));
                localTableMap.put(tableKey, existingTable);
                tableUpdated = true;
                updatedTables++;
            }

            Map<String, DbTableFieldMetaDTO> tableFieldMap = localFieldMap.computeIfAbsent(tableKey, key -> new LinkedHashMap<>());
            for (DbColumnMeta remoteField : remoteFields == null ? List.<DbColumnMeta>of() : remoteFields) {
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
                } else if (hasPersistentId(existingField.getId())) {
                    DbTableFieldMetaDTO updatedField = tableFieldMetaService.update(existingField.getId(), buildFieldMeta(sourceKey, remoteField, existingField));
                    tableFieldMap.put(fieldKey, updatedField);
                    fieldUpdatedCount++;
                    updatedFields++;
                }
            }

            Map<String, DbTableIndexMetaDTO> tableIndexMap = localIndexMap.computeIfAbsent(tableKey, key -> new LinkedHashMap<>());
            for (DbIndexMeta remoteIndex : remoteIndexes == null ? List.<DbIndexMeta>of() : remoteIndexes) {
                if (remoteIndex == null || remoteIndex.getIndexName() == null || remoteIndex.getColumnName() == null) {
                    continue;
                }
                String indexKey = buildIndexKey(remoteIndex.getIndexName(), remoteIndex.getColumnName());
                DbTableIndexMetaDTO existingIndex = tableIndexMap.get(indexKey);
                if (existingIndex == null) {
                    DbTableIndexMetaDTO createdIndex = tableIndexMetaService.add(buildIndexMeta(sourceKey, remoteIndex, null));
                    tableIndexMap.put(indexKey, createdIndex);
                    indexCreatedCount++;
                    createdIndexes++;
                } else if (hasPersistentId(existingIndex.getId())) {
                    DbTableIndexMetaDTO updatedIndex = tableIndexMetaService.update(existingIndex.getId(), buildIndexMeta(sourceKey, remoteIndex, existingIndex));
                    tableIndexMap.put(indexKey, updatedIndex);
                    indexUpdatedCount++;
                    updatedIndexes++;
                }
            }

            DbAccessTableSyncItem item = new DbAccessTableSyncItem();
            item.setTableName(remoteTable.getTableName());
            item.setTableCreated(tableCreated);
            item.setTableUpdated(tableUpdated);
            item.setFieldCreatedCount(fieldCreatedCount);
            item.setFieldUpdatedCount(fieldUpdatedCount);
            item.setRemoteFieldCount(remoteFields == null ? 0 : remoteFields.size());
            item.setIndexCreatedCount(indexCreatedCount);
            item.setIndexUpdatedCount(indexUpdatedCount);
            item.setRemoteIndexCount(remoteIndexes == null ? 0 : remoteIndexes.size());
            items.add(item);
        }

        DbAccessTableSyncResponse response = new DbAccessTableSyncResponse();
        response.setSourceKey(sourceKey);
        response.setAllowUpdate(Boolean.TRUE);
        response.setCreatedTableCount(createdTables);
        response.setUpdatedTableCount(updatedTables);
        response.setCreatedFieldCount(createdFields);
        response.setUpdatedFieldCount(updatedFields);
        response.setCreatedIndexCount(createdIndexes);
        response.setUpdatedIndexCount(updatedIndexes);
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

    private Map<String, Map<String, DbTableIndexMetaDTO>> loadLocalIndexes(String sourceKey) {
        DbTableIndexMetaQueryRequest query = new DbTableIndexMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        Map<String, Map<String, DbTableIndexMetaDTO>> result = new LinkedHashMap<>();
        for (DbTableIndexMetaDTO item : tableIndexMetaService.queryAll(query)) {
            result.computeIfAbsent(normalizeKey(item.getTableName()), key -> new LinkedHashMap<>())
                    .put(buildIndexKey(item.getIndexName(), item.getColumnName()), item);
        }
        return result;
    }

    private DbTableMetaDTO buildTableMeta(
            String sourceKey,
            DbTableMeta remoteTable,
            List<DbColumnMeta> remoteFields,
            DbTableMetaDTO existing
    ) {
        // 保留分层、启用状态、备注等人工配置，只刷新数据库提供的基础结构信息。
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

    private DbTableIndexMetaDTO buildIndexMeta(String sourceKey, DbIndexMeta remoteIndex, DbTableIndexMetaDTO existing) {
        // 保留 enabled、remark 等人工配置，只刷新索引结构信息。
        DbTableIndexMetaDTO dto = existing == null ? new DbTableIndexMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
        dto.setTableName(remoteIndex.getTableName());
        dto.setIndexName(remoteIndex.getIndexName());
        dto.setIndexType(resolveIndexType(remoteIndex));
        dto.setUniqueFlag(remoteIndex.getUniqueFlag());
        dto.setPrimaryFlag(remoteIndex.getPrimaryFlag());
        dto.setColumnName(remoteIndex.getColumnName());
        dto.setColumnOrder(remoteIndex.getColumnOrder());
        dto.setEnabled(existing == null ? Boolean.TRUE : existing.getEnabled());
        return dto;
    }

    private String resolveIndexType(DbIndexMeta remoteIndex) {
        if (Boolean.TRUE.equals(remoteIndex.getPrimaryFlag())) {
            return "PRIMARY";
        }
        return Boolean.TRUE.equals(remoteIndex.getUniqueFlag()) ? "UNIQUE" : "NORMAL";
    }

    private String requireSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw BizException.of();
        }
        return sourceKey.trim();
    }

    private String requireTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw BizException.of();
        }
        return tableName.trim();
    }

    private void requireRegisteredTable(String sourceKey, String tableName) {
        if (!loadLocalTables(sourceKey).containsKey(normalizeKey(tableName))) {
            throw BizException.of(DbEngineBizCodeConstant.TABLE_META_NOT_FOUND, tableName);
        }
    }

    private int normalizePreviewPage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePreviewPageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }

    private List<String> resolvePreviewColumns(List<Map<String, Object>> records) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            if (record != null) {
                columns.addAll(record.keySet());
            }
        }
        return new ArrayList<>(columns);
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

    private String buildIndexKey(String indexName, String columnName) {
        return normalizeKey(indexName) + "|" + normalizeKey(columnName);
    }

    private boolean hasPersistentId(Object id) {
        return id != null && !String.valueOf(id).isBlank();
    }
}
