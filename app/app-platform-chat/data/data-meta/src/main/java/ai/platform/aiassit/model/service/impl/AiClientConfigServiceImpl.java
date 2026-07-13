package ai.platform.aiassit.model.service.impl;

import ai.platform.aiassit.model.entity.AiClientConfigEntity;
import ai.platform.aiassit.model.entity.AiModelConfigEntity;
import ai.platform.aiassit.model.entity.dto.AiClientConfigDTO;
import ai.platform.aiassit.model.entity.vo.AiClientConfigVO;
import ai.platform.aiassit.model.mapper.AiClientConfigMapper;
import ai.platform.aiassit.model.mapper.AiModelConfigMapper;
import ai.platform.aiassit.model.service.AiClientConfigService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiClientConfigServiceImpl implements AiClientConfigService {
    private final AiClientConfigMapper clientMapper;
    private final AiModelConfigMapper modelMapper;

    public AiClientConfigServiceImpl(AiClientConfigMapper clientMapper, AiModelConfigMapper modelMapper) {
        this.clientMapper = clientMapper;
        this.modelMapper = modelMapper;
    }

    @Override
    public List<AiClientConfigVO> list() {
        return clientMapper.selectList(Wrappers.<AiClientConfigEntity>lambdaQuery()
                        .orderByDesc(AiClientConfigEntity::getUpdateTime)
                        .orderByDesc(AiClientConfigEntity::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public AiClientConfigDTO require(Long id) {
        AiClientConfigEntity entity = id == null ? null : clientMapper.selectById(id);
        if (entity == null) throw BizException.of(AiChatBizCodeConstant.AI_CLIENT_CONFIG_NOT_FOUND, id);
        return toDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiClientConfigVO add(AiClientConfigDTO dto) {
        validate(dto, true);
        AiClientConfigEntity entity = toEntity(dto);
        clientMapper.insert(entity);
        return toVO(clientMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiClientConfigVO update(Long id, AiClientConfigDTO dto) {
        AiClientConfigEntity current = clientMapper.selectById(id);
        if (current == null) throw BizException.of(AiChatBizCodeConstant.AI_CLIENT_CONFIG_NOT_FOUND, id);
        AiClientConfigDTO merged = toDTO(current);
        if (StringUtils.hasText(dto.getClientCode())) merged.setClientCode(dto.getClientCode().trim());
        if (StringUtils.hasText(dto.getClientName())) merged.setClientName(dto.getClientName().trim());
        if (dto.getClientType() != null) merged.setClientType(dto.getClientType());
        if (dto.getBaseUrl() != null) merged.setBaseUrl(trimToNull(dto.getBaseUrl()));
        if (StringUtils.hasText(dto.getApiKey())) merged.setApiKey(dto.getApiKey().trim());
        if (dto.getEnabled() != null) merged.setEnabled(dto.getEnabled());
        if (dto.getExtJson() != null) merged.setExtJson(dto.getExtJson());
        validate(merged, false);
        AiClientConfigEntity entity = toEntity(merged);
        entity.setId(id);
        clientMapper.updateById(entity);
        modelMapper.update(null, Wrappers.<AiModelConfigEntity>lambdaUpdate()
                .eq(AiModelConfigEntity::getClientId, id)
                .set(AiModelConfigEntity::getClientType, merged.getClientType())
                .set(AiModelConfigEntity::getBaseUrl, merged.getBaseUrl())
                .set(AiModelConfigEntity::getApiKey, merged.getApiKey()));
        return toVO(clientMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        long modelCount = modelMapper.selectCount(Wrappers.<AiModelConfigEntity>lambdaQuery()
                .eq(AiModelConfigEntity::getClientId, id));
        if (modelCount > 0) throw BizException.of(AiChatBizCodeConstant.AI_CLIENT_IN_USE, modelCount);
        return clientMapper.deleteById(id) > 0;
    }

    private void validate(AiClientConfigDTO dto, boolean creating) {
        if (!StringUtils.hasText(dto.getClientCode())) throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_AI_CLIENT_CODE);
        if (!StringUtils.hasText(dto.getClientName())) throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_AI_CLIENT_NAME);
        if (dto.getClientType() == null) throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CHAT_CLIENT_TYPE);
        if (dto.getClientType() == AiChatClientType.SPRING_AI && !StringUtils.hasText(dto.getBaseUrl()))
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_BASE_URL);
        if (creating && !StringUtils.hasText(dto.getApiKey())) throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
    }

    private AiClientConfigEntity toEntity(AiClientConfigDTO dto) {
        AiClientConfigEntity entity = new AiClientConfigEntity();
        entity.setClientCode(trimToNull(dto.getClientCode())); entity.setClientName(trimToNull(dto.getClientName()));
        entity.setClientType(dto.getClientType()); entity.setBaseUrl(trimToNull(dto.getBaseUrl()));
        entity.setApiKey(trimToNull(dto.getApiKey())); entity.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        entity.setExtJson(dto.getExtJson()); return entity;
    }

    private AiClientConfigDTO toDTO(AiClientConfigEntity entity) {
        AiClientConfigDTO dto = new AiClientConfigDTO(); dto.setId(entity.getId()); dto.setClientCode(entity.getClientCode());
        dto.setClientName(entity.getClientName()); dto.setClientType(entity.getClientType()); dto.setBaseUrl(entity.getBaseUrl());
        dto.setApiKey(entity.getApiKey()); dto.setEnabled(entity.getEnabled()); dto.setExtJson(entity.getExtJson()); return dto;
    }

    private AiClientConfigVO toVO(AiClientConfigEntity entity) {
        AiClientConfigVO vo = new AiClientConfigVO(); vo.setId(entity.getId()); vo.setClientCode(entity.getClientCode());
        vo.setClientName(entity.getClientName()); vo.setClientType(entity.getClientType()); vo.setBaseUrl(entity.getBaseUrl());
        vo.setApiKeyMasked(mask(entity.getApiKey())); vo.setEnabled(entity.getEnabled()); vo.setExtJson(entity.getExtJson());
        vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        vo.setModelCount(Math.toIntExact(modelMapper.selectCount(Wrappers.<AiModelConfigEntity>lambdaQuery().eq(AiModelConfigEntity::getClientId, entity.getId()))));
        return vo;
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String mask(String value) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() <= 8 ? text.substring(0, 1) + "****" + text.substring(text.length() - 1)
                : text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }
}
