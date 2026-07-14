package ai.platform.aiassit.db.engine.virtualization.adapter.physical;

import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalCatalogPort;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalFieldDefinition;
import ai.platform.aiassit.data.virtualization.spi.catalog.PhysicalTableDefinition;
import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableFieldMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableMetaMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Maps DB Engine metadata persistence models to the virtualization physical catalog contract. */
@Component
public class DbEnginePhysicalCatalogAdapter implements PhysicalCatalogPort {

    private final DbTableMetaMapper tableMapper;
    private final DbTableFieldMetaMapper fieldMapper;

    public DbEnginePhysicalCatalogAdapter(DbTableMetaMapper tableMapper, DbTableFieldMetaMapper fieldMapper) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
    }

    @Override
    public Optional<PhysicalTableDefinition> findTable(long tableMetaId) {
        return Optional.ofNullable(tableMapper.selectById(tableMetaId)).map(this::table);
    }

    @Override
    public List<PhysicalFieldDefinition> fields(long tableMetaId) {
        DbTableMetaEntity table = tableMapper.selectById(tableMetaId);
        if (table == null) {
            return List.of();
        }
        return fieldMapper.selectList(Wrappers.<DbTableFieldMetaEntity>lambdaQuery()
                        .eq(DbTableFieldMetaEntity::getSourceKey, table.getSourceKey())
                        .eq(DbTableFieldMetaEntity::getTableName, table.getTableName())
                        .orderByAsc(DbTableFieldMetaEntity::getOrdinalPosition))
                .stream()
                .map(this::field)
                .toList();
    }

    @Override
    public Optional<PhysicalFieldDefinition> findField(long fieldMetaId) {
        return Optional.ofNullable(fieldMapper.selectById(fieldMetaId)).map(this::field);
    }

    private PhysicalTableDefinition table(DbTableMetaEntity source) {
        return new PhysicalTableDefinition(
                source.getId(),
                source.getSourceKey(),
                source.getTableName(),
                Boolean.TRUE.equals(source.getEnabled())
        );
    }

    private PhysicalFieldDefinition field(DbTableFieldMetaEntity source) {
        return new PhysicalFieldDefinition(
                source.getId(),
                source.getSourceKey(),
                source.getTableName(),
                source.getColumnName(),
                source.getColumnComment(),
                source.getDataType(),
                Boolean.TRUE.equals(source.getNullable()),
                Boolean.TRUE.equals(source.getPrimaryKey()),
                source.getDefaultValue(),
                source.getOrdinalPosition(),
                Boolean.TRUE.equals(source.getEnabled())
        );
    }
}
