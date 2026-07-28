package ai.platform.aiassit.user.system.settings.data.service.impl;

import ai.platform.aiassit.user.system.settings.data.convert.SystemSettingConvert;
import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingImportResultDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingTransferDocument;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingExportRequest;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingQueryRequest;
import ai.platform.aiassit.user.system.settings.data.mapper.SystemSettingMapper;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemSettingServiceImpl
        extends BaseMapperService<SystemSettingEntity, SystemSettingMapper, SystemSettingDTO>
        implements SystemSettingService {

    private final SystemSettingConvert systemSettingConvert;
    private final ObjectMapper objectMapper;

    public SystemSettingServiceImpl(SystemSettingConvert systemSettingConvert, ObjectMapper objectMapper) {
        this.systemSettingConvert = systemSettingConvert;
        this.objectMapper = objectMapper;
    }

    @Override
    protected IConvert<SystemSettingEntity, SystemSettingDTO> convert() {
        return systemSettingConvert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<SystemSettingEntity> buildQuery(Query query) {
        QueryWrapper<SystemSettingEntity> qw = super.buildQuery(query);
        if (query instanceof SystemSettingQueryRequest request) {
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = request.getKeyword();
                qw.like("setting_key", keyword)
                        .or()
                        .like("setting_value", keyword)
                        .or()
                        .like("description", keyword);
            }
        }


        return qw;
    }

    @Override
    public String queryValueByKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        SystemSettingEntity entity = baseMapper.selectBySettingKey(key);
        if (entity == null || Boolean.FALSE.equals(entity.getEnabled())) {
            return null;
        }
        return entity.getSettingValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemSettingImportResultDTO importJsonFile(MultipartFile file) throws IOException {
        SystemSettingImportResultDTO result = new SystemSettingImportResultDTO();
        if (file == null || file.isEmpty()) {
            return result;
        }

        List<SystemSettingTransferDocument> documents = objectMapper.readValue(
                file.getInputStream(),
                new TypeReference<>() {
                }
        );
        if (CollectionUtils.isEmpty(documents)) {
            return result;
        }

        result.setReceived(documents.size());
        for (SystemSettingTransferDocument document : documents) {
            if (document == null || !StringUtils.hasText(document.getSettingKey())) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }

            String settingKey = document.getSettingKey().trim();
            SystemSettingEntity entity = baseMapper.selectBySettingKey(settingKey);
            boolean creating = entity == null;
            if (creating) {
                entity = new SystemSettingEntity();
                entity.setSettingKey(settingKey);
            }

            entity.setDescription(document.getDescription());
            entity.setSettingValue(document.getSettingValue());
            entity.setValueType(StringUtils.hasText(document.getValueType())
                    ? document.getValueType().trim()
                    : "STRING");
            entity.setEnabled(document.getEnabled() == null || document.getEnabled());

            if (creating) {
                baseMapper.insert(entity);
                result.setCreated(result.getCreated() + 1);
            } else {
                baseMapper.updateById(entity);
                result.setUpdated(result.getUpdated() + 1);
            }
        }
        return result;
    }

    @Override
    public List<SystemSettingTransferDocument> exportJson(SystemSettingExportRequest request) {
        QueryWrapper<SystemSettingEntity> query = new QueryWrapper<>();
        List<String> settingKeys = normalizeSettingKeys(request == null ? null : request.getSettingKeys());
        if (!settingKeys.isEmpty()) {
            query.in("setting_key", settingKeys);
        } else if (request != null && StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            query.and(wrapper -> wrapper.like("setting_key", keyword)
                    .or()
                    .like("setting_value", keyword)
                    .or()
                    .like("description", keyword));
        }
        query.orderByAsc("setting_key");

        List<SystemSettingTransferDocument> documents = new ArrayList<>();
        for (SystemSettingEntity entity : baseMapper.selectList(query)) {
            SystemSettingTransferDocument document = new SystemSettingTransferDocument();
            document.setSettingKey(entity.getSettingKey());
            document.setDescription(entity.getDescription());
            document.setSettingValue(entity.getSettingValue());
            document.setValueType(entity.getValueType());
            document.setEnabled(entity.getEnabled());
            documents.add(document);
        }
        return documents;
    }

    private List<String> normalizeSettingKeys(List<String> settingKeys) {
        if (CollectionUtils.isEmpty(settingKeys)) {
            return List.of();
        }
        return settingKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
