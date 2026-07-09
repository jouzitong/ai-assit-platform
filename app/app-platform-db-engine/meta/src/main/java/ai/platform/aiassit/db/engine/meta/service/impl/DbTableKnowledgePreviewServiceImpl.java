package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgePreviewDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableKnowledgePreviewService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

@Service
public class DbTableKnowledgePreviewServiceImpl implements DbTableKnowledgePreviewService {

    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService fieldMetaService;

    public DbTableKnowledgePreviewServiceImpl(
            DbTableMetaService tableMetaService,
            DbTableFieldMetaService fieldMetaService
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
    }

    @Override
    public DbTableKnowledgePreviewDTO preview(String sourceKey, String tableName) {
        if (!StringUtils.hasText(sourceKey)) {
            throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_SOURCE_KEY);
        }
        if (!StringUtils.hasText(tableName)) {
            throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_TABLE_NAME);
        }

        DbTableMetaDTO tableMeta = findTable(sourceKey, tableName);
        List<DbTableFieldMetaDTO> fields = findFields(sourceKey, tableName);
        return preview(tableMeta, fields);
    }

    @Override
    public DbTableKnowledgePreviewDTO preview(DbTableMetaDTO tableMeta, List<DbTableFieldMetaDTO> fields) {
        DbTableKnowledgePreviewDTO preview = new DbTableKnowledgePreviewDTO();
        preview.setType("markdown");
        preview.setContent(buildMarkdown(tableMeta, fields));
        return preview;
    }

    private DbTableMetaDTO findTable(String sourceKey, String tableName) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return tableMetaService.queryAll(query).stream()
                .filter(item -> tableName.equals(item.getTableName()))
                .findFirst()
                .orElseThrow(() -> BizException.of(DbEngineBizCodeConstant.TABLE_META_NOT_FOUND, tableName));
    }

    private List<DbTableFieldMetaDTO> findFields(String sourceKey, String tableName) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return fieldMetaService.queryAll(query).stream()
                .sorted(Comparator
                        .comparing(DbTableFieldMetaDTO::getOrdinalPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DbTableFieldMetaDTO::getColumnName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private String buildMarkdown(DbTableMetaDTO tableMeta, List<DbTableFieldMetaDTO> fields) {
        String tableLabel = firstNonBlank(tableMeta.getTableComment(), tableMeta.getTableName());
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(tableMeta.getTableName()).append(' ').append(tableLabel).append("\n\n");
        builder.append("## 表说明\n");
        builder.append(firstNonBlank(tableMeta.getTableComment(), "暂无表说明。")).append("\n\n");
        builder.append("## 字段\n");
        builder.append("| 字段名 | 中文名 | 类型 | 说明 |\n");
        builder.append("|---|---|---|---|\n");
        for (DbTableFieldMetaDTO field : fields) {
            builder.append("| ")
                    .append(valueOrDash(field.getColumnName())).append(" | ")
                    .append(valueOrDash(firstNonBlank(field.getColumnComment(), field.getColumnName()))).append(" | ")
                    .append(valueOrDash(field.getDataType())).append(" | ")
                    .append(valueOrDash(buildFieldDescription(field))).append(" |\n");
        }
        builder.append("\n");
        builder.append("## 常见查询\n");
        builder.append("- 查询").append(tableLabel).append("列表\n");
        builder.append("- 统计").append(tableLabel).append("总量\n");
        if (StringUtils.hasText(tableMeta.getPartitionKey()) && !"none".equalsIgnoreCase(tableMeta.getPartitionKey())) {
            builder.append("- 按分区字段 ").append(tableMeta.getPartitionKey()).append(" 查询").append(tableLabel).append("\n");
        } else {
            builder.append("- 按条件筛选").append(tableLabel).append("数据\n");
        }
        return builder.toString().trim();
    }

    private String buildFieldDescription(DbTableFieldMetaDTO field) {
        StringJoiner joiner = new StringJoiner("；");
        if (Boolean.TRUE.equals(field.getPrimaryKey())) {
            joiner.add("主键");
        }
        if (Boolean.TRUE.equals(field.getPartitionKey())) {
            joiner.add("分区字段");
        }
        if (Boolean.FALSE.equals(field.getNullable())) {
            joiner.add("非空");
        }
        if (StringUtils.hasText(field.getFieldRole())) {
            joiner.add(resolveFieldRoleLabel(field.getFieldRole()));
        }
        if (StringUtils.hasText(field.getRemark()) && !field.getRemark().equals(field.getColumnComment())) {
            joiner.add(field.getRemark());
        }
        return joiner.length() > 0 ? joiner.toString() : firstNonBlank(field.getColumnComment(), field.getRemark(), "-");
    }

    private String resolveFieldRoleLabel(String fieldRole) {
        String normalizedRole = String.valueOf(fieldRole).toUpperCase();
        switch (normalizedRole) {
            case "PRIMARY_KEY":
                return "主键字段";
            case "PARTITION_KEY":
                return "分区字段";
            case "DIMENSION":
                return "维度字段";
            case "METRIC":
                return "指标字段";
            case "FACT":
                return "事实字段";
            case "TIME":
                return "时间字段";
            default:
                return fieldRole;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
