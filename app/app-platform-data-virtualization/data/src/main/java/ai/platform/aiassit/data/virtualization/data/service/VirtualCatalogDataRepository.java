package ai.platform.aiassit.data.virtualization.data.service;

import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualBindingEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualEntityEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualFieldEntity;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import ai.platform.aiassit.data.virtualization.data.mapper.FieldTransformPortMapper;
import ai.platform.aiassit.data.virtualization.data.mapper.FieldTransformRuleMapper;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualBindingMapper;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualEntityMapper;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualFieldMapper;
import ai.platform.aiassit.data.virtualization.data.mapper.VirtualRelationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** 为目录聚合提供集中、可测试的数据访问入口。 */
@Repository
public class VirtualCatalogDataRepository {
    private final VirtualEntityMapper entityMapper;
    private final VirtualFieldMapper fieldMapper;
    private final VirtualBindingMapper bindingMapper;
    private final FieldTransformRuleMapper ruleMapper;
    private final FieldTransformPortMapper portMapper;
    private final VirtualRelationMapper relationMapper;

    public VirtualCatalogDataRepository(
            VirtualEntityMapper entityMapper,
            VirtualFieldMapper fieldMapper,
            VirtualBindingMapper bindingMapper,
            FieldTransformRuleMapper ruleMapper,
            FieldTransformPortMapper portMapper,
            VirtualRelationMapper relationMapper
    ) {
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.bindingMapper = bindingMapper;
        this.ruleMapper = ruleMapper;
        this.portMapper = portMapper;
        this.relationMapper = relationMapper;
    }

    public VirtualEntityEntity entityById(Long id) {
        return id == null ? null : entityMapper.selectById(id);
    }

    public VirtualEntityEntity entityByCode(String code) {
        return entityMapper.selectOne(Wrappers.<VirtualEntityEntity>lambdaQuery()
                .eq(VirtualEntityEntity::getEntityCode, code));
    }

    public List<VirtualFieldEntity> fields(Long entityId) {
        return fieldMapper.selectList(Wrappers.<VirtualFieldEntity>lambdaQuery()
                .eq(VirtualFieldEntity::getEntityId, entityId)
                .orderByAsc(VirtualFieldEntity::getOrdinalPosition, VirtualFieldEntity::getId));
    }

    public List<VirtualBindingEntity> bindings(Long entityId) {
        return bindingMapper.selectList(Wrappers.<VirtualBindingEntity>lambdaQuery()
                .eq(VirtualBindingEntity::getEntityId, entityId)
                .orderByAsc(VirtualBindingEntity::getBindingGroup, VirtualBindingEntity::getBindingCode));
    }

    public List<FieldTransformRuleEntity> rules(Collection<Long> bindingIds) {
        if (bindingIds == null || bindingIds.isEmpty()) {
            return List.of();
        }
        return ruleMapper.selectList(Wrappers.<FieldTransformRuleEntity>lambdaQuery()
                .in(FieldTransformRuleEntity::getBindingId, bindingIds)
                .orderByAsc(FieldTransformRuleEntity::getBindingId, FieldTransformRuleEntity::getId));
    }

    public FieldTransformRuleEntity ruleById(Long ruleId) {
        return ruleId == null ? null : ruleMapper.selectById(ruleId);
    }

    public List<FieldTransformPortEntity> ports(Collection<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return List.of();
        }
        return portMapper.selectList(Wrappers.<FieldTransformPortEntity>lambdaQuery()
                .in(FieldTransformPortEntity::getRuleId, ruleIds)
                .orderByAsc(FieldTransformPortEntity::getRuleId, FieldTransformPortEntity::getOrdinalPosition));
    }

    public List<FieldTransformPortEntity> portsByRule(Long ruleId) {
        return portMapper.selectList(Wrappers.<FieldTransformPortEntity>lambdaQuery()
                .eq(FieldTransformPortEntity::getRuleId, ruleId)
                .orderByAsc(FieldTransformPortEntity::getOrdinalPosition));
    }

    public List<FieldTransformPortEntity> portsByVirtualField(Long fieldId) {
        return portMapper.selectList(Wrappers.<FieldTransformPortEntity>lambdaQuery()
                .eq(FieldTransformPortEntity::getVirtualFieldId, fieldId));
    }

    public List<FieldTransformPortEntity> portsByPhysicalField(Long fieldId) {
        return portMapper.selectList(Wrappers.<FieldTransformPortEntity>lambdaQuery()
                .eq(FieldTransformPortEntity::getPhysicalFieldMetaId, fieldId));
    }

    public List<VirtualRelationEntity> relations(Long entityId) {
        return relationMapper.selectList(Wrappers.<VirtualRelationEntity>lambdaQuery()
                .and(wrapper -> wrapper.eq(VirtualRelationEntity::getSourceEntityId, entityId)
                        .or().eq(VirtualRelationEntity::getTargetEntityId, entityId))
                .orderByAsc(VirtualRelationEntity::getRelationCode, VirtualRelationEntity::getId));
    }

    public VirtualFieldEntity fieldById(Long fieldId) {
        return fieldId == null ? null : fieldMapper.selectById(fieldId);
    }

    public void insertEntity(VirtualEntityEntity entity) { entityMapper.insert(entity); }
    public void insertField(VirtualFieldEntity field) { fieldMapper.insert(field); }
    public void insertBinding(VirtualBindingEntity binding) { bindingMapper.insert(binding); }
    public void insertRule(FieldTransformRuleEntity rule) { ruleMapper.insert(rule); }
    public void insertPort(FieldTransformPortEntity port) { portMapper.insert(port); }
    public void updateEntity(VirtualEntityEntity entity) { entityMapper.updateById(entity); }
}
