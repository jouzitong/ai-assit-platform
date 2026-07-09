package ai.platform.aiassit.user.errcode.data.service.impl;

import ai.platform.aiassit.user.errcode.data.convert.ErrCodeConvert;
import ai.platform.aiassit.user.errcode.data.entity.ErrCodeEntity;
import ai.platform.aiassit.user.errcode.data.entity.ErrCodeI18nEntity;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeResolveDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertRequest;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertResultDTO;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeUpsertValue;
import ai.platform.aiassit.user.errcode.data.entity.req.ErrCodeQueryRequest;
import ai.platform.aiassit.user.errcode.data.mapper.ErrCodeI18nMapper;
import ai.platform.aiassit.user.errcode.data.mapper.ErrCodeMapper;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ErrCodeServiceImpl
        extends BaseMapperService<ErrCodeEntity, ErrCodeMapper, ErrCodeDTO>
        implements ErrCodeService {

    private static final int DEFAULT_HTTP_STATUS = 200;

    private final ErrCodeConvert errCodeConvert;
    private final ErrCodeI18nMapper errCodeI18nMapper;
    private final ObjectMapper objectMapper;

    public ErrCodeServiceImpl(ErrCodeConvert errCodeConvert,
                              ErrCodeI18nMapper errCodeI18nMapper,
                              ObjectMapper objectMapper) {
        this.errCodeConvert = errCodeConvert;
        this.errCodeI18nMapper = errCodeI18nMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected IConvert<ErrCodeEntity, ErrCodeDTO> convert() {
        return errCodeConvert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<ErrCodeEntity> buildQuery(Query query) {
        QueryWrapper<ErrCodeEntity> qw = super.buildQuery(query);
        if (query instanceof ErrCodeQueryRequest request) {
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = request.getKeyword();
                qw.and(wrapper -> wrapper.like("code", keyword)
                        .or()
                        .like("description", keyword)
                        .or()
                        .like("tags", keyword));
            }
            qw.orderByDesc("update_time").orderByDesc("id");
        }
        return qw;
    }

    @Override
    public ErrCodeResolveDTO resolve(Integer code, String locale) {
        ErrCodeResolveDTO dto = new ErrCodeResolveDTO();
        dto.setCode(code);
        dto.setHttpStatus(DEFAULT_HTTP_STATUS);

        if (code == null) {
            return dto;
        }

        ErrCodeEntity errCode = baseMapper.selectByCode(code);
        if (errCode != null) {
            dto.setHttpStatus(errCode.getHttpStatus() == null ? DEFAULT_HTTP_STATUS : errCode.getHttpStatus());
            dto.setDescription(errCode.getDescription());
        }

        ErrCodeI18nEntity i18n = null;
        if (StringUtils.hasText(locale)) {
            i18n = errCodeI18nMapper.selectByErrCodeAndLocale(code, locale);
        }
        if (i18n == null) {
            i18n = errCodeI18nMapper.selectFirstByErrCode(code);
        }
        if (i18n != null) {
            dto.setLocale(i18n.getLocale());
            dto.setMessageTemplate(i18n.getMessageTemplate());
            dto.setDescription(i18n.getDescription());
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErrCodeUpsertResultDTO upsertJson(List<ErrCodeUpsertRequest> documents) {
        ErrCodeUpsertResultDTO result = new ErrCodeUpsertResultDTO();
        if (CollectionUtils.isEmpty(documents)) {
            return result;
        }

        result.setReceived(documents.size());
        for (ErrCodeUpsertRequest document : documents) {
            if (document == null || document.getCode() == null) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }

            upsertErrCode(document);
            result.setErrCodeUpserted(result.getErrCodeUpserted() + 1);

            if (CollectionUtils.isEmpty(document.getValue())) {
                continue;
            }
            for (ErrCodeUpsertValue value : document.getValue()) {
                if (value == null || !StringUtils.hasText(value.getLocale())) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                upsertErrCodeI18n(document.getCode(), value);
                result.setI18nUpserted(result.getI18nUpserted() + 1);
            }
        }
        return result;
    }

    @Override
    public ErrCodeUpsertResultDTO importJsonFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return new ErrCodeUpsertResultDTO();
        }
        List<ErrCodeUpsertRequest> documents = objectMapper.readValue(
                file.getInputStream(),
                new TypeReference<>() {
                }
        );
        return upsertJson(documents);
    }

    @Override
    public List<ErrCodeUpsertRequest> exportJson() {
        List<ErrCodeEntity> codes = baseMapper.selectList(new QueryWrapper<ErrCodeEntity>().orderByAsc("code"));
        List<ErrCodeI18nEntity> i18nList = errCodeI18nMapper.selectList(new QueryWrapper<ErrCodeI18nEntity>().orderByAsc("err_code").orderByAsc("locale"));
        Map<Integer, List<ErrCodeUpsertValue>> valuesByCode = new LinkedHashMap<>();
        for (ErrCodeI18nEntity i18n : i18nList) {
            if (i18n.getErrCode() == null) {
                continue;
            }
            ErrCodeUpsertValue value = new ErrCodeUpsertValue();
            value.setLocale(i18n.getLocale());
            value.setMessageTemplate(i18n.getMessageTemplate());
            value.setDescription(i18n.getDescription());
            valuesByCode.computeIfAbsent(i18n.getErrCode(), key -> new ArrayList<>()).add(value);
        }

        List<ErrCodeUpsertRequest> documents = new ArrayList<>();
        for (ErrCodeEntity code : codes) {
            ErrCodeUpsertRequest document = new ErrCodeUpsertRequest();
            document.setCode(code.getCode());
            document.setHttpStatus(code.getHttpStatus());
            document.setDescription(code.getDescription());
            document.setTags(code.getTags());
            document.setValue(valuesByCode.getOrDefault(code.getCode(), List.of()));
            documents.add(document);
        }
        return documents;
    }

    private void upsertErrCode(ErrCodeUpsertRequest document) {
        ErrCodeEntity entity = baseMapper.selectByCode(document.getCode());
        if (entity == null) {
            entity = new ErrCodeEntity();
            entity.setCode(document.getCode());
            entity.setHttpStatus(document.getHttpStatus() == null ? DEFAULT_HTTP_STATUS : document.getHttpStatus());
            entity.setDescription(document.getDescription());
            entity.setTags(document.getTags());
            baseMapper.insert(entity);
            return;
        }

        entity.setHttpStatus(document.getHttpStatus() == null ? DEFAULT_HTTP_STATUS : document.getHttpStatus());
        entity.setDescription(document.getDescription());
        entity.setTags(document.getTags());
        baseMapper.updateById(entity);
    }

    private void upsertErrCodeI18n(Integer code, ErrCodeUpsertValue value) {
        String locale = value.getLocale().trim();
        ErrCodeI18nEntity entity = errCodeI18nMapper.selectByErrCodeAndLocale(code, locale);
        if (entity == null) {
            entity = new ErrCodeI18nEntity();
            entity.setErrCode(code);
            entity.setLocale(locale);
            entity.setMessageTemplate(value.getMessageTemplate());
            entity.setDescription(value.getDescription());
            errCodeI18nMapper.insert(entity);
            return;
        }

        entity.setMessageTemplate(value.getMessageTemplate());
        entity.setDescription(value.getDescription());
        errCodeI18nMapper.updateById(entity);
    }
}
