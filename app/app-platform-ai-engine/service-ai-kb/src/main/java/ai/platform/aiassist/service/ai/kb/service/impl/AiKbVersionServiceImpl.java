package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
import ai.platform.aiassist.service.ai.kb.convert.AiKbVersionConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbVersionEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbVersionQueryRequest;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbVersionMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbVersionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiKbVersionServiceImpl
        extends BaseMapperService<AiKbVersionEntity, AiKbVersionMapper, AiKbVersionDTO>
        implements AiKbVersionService {

    private final AiKbVersionConvert convert;

    public AiKbVersionServiceImpl(AiKbVersionConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbVersionEntity, AiKbVersionDTO> convert() {
        return convert;
    }

    @Override
    public AiKbVersionDTO getDraftVersion(String kbCode) {
        return getByStatus(kbCode, AiKbVersionStatus.DRAFT);
    }

    @Override
    public AiKbVersionDTO getCurrentVersion(String kbCode) {
        return getByStatus(kbCode, AiKbVersionStatus.CURRENT);
    }

    @Override
    public AiKbVersionDTO getVersion(String kbCode, Integer versionNo) {
        if (!StringUtils.hasText(kbCode) || versionNo == null) {
            return null;
        }
        AiKbVersionQueryRequest query = new AiKbVersionQueryRequest();
        query.setKbCode(kbCode.trim());
        query.setVersionNo(versionNo);
        query.setPage(1);
        query.setSize(1);
        List<AiKbVersionDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKbVersionDTO getVersion(Long kbVersionId) {
        if (kbVersionId == null) {
            return null;
        }
        AiKbVersionQueryRequest query = new AiKbVersionQueryRequest();
        query.setId(kbVersionId);
        query.setPage(1);
        query.setSize(1);
        List<AiKbVersionDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AiKbVersionDTO> listDraftVersions() {
        AiKbVersionQueryRequest query = new AiKbVersionQueryRequest();
        query.setStatus(AiKbVersionStatus.DRAFT);
        query.setOrderByVersionNoDesc(Boolean.TRUE);
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        return queryAll(query);
    }

    private AiKbVersionDTO getByStatus(String kbCode, AiKbVersionStatus status) {
        if (!StringUtils.hasText(kbCode)) {
            return null;
        }
        AiKbVersionQueryRequest query = new AiKbVersionQueryRequest();
        query.setKbCode(kbCode.trim());
        query.setStatus(status);
        query.setOrderByVersionNoDesc(Boolean.TRUE);
        query.setPage(1);
        query.setSize(1);
        List<AiKbVersionDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Integer getMaxVersionNo(String kbCode) {
        if (!StringUtils.hasText(kbCode)) {
            return null;
        }
        AiKbVersionQueryRequest query = new AiKbVersionQueryRequest();
        query.setKbCode(kbCode.trim());
        query.setOrderByVersionNoDesc(Boolean.TRUE);
        query.setPage(1);
        query.setSize(1);
        List<AiKbVersionDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0).getVersionNo();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbVersionEntity> buildQuery(Query query) {
        QueryWrapper<AiKbVersionEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiKbVersionQueryRequest req) {
            if (req.getId() != null) {
                wrapper.lambda().eq(AiKbVersionEntity::getId, req.getId());
            }
            if (StringUtils.hasText(req.getKbCode())) {
                wrapper.lambda().eq(AiKbVersionEntity::getKbCode, req.getKbCode().trim());
            }
            if (req.getVersionNo() != null) {
                wrapper.lambda().eq(AiKbVersionEntity::getVersionNo, req.getVersionNo());
            }
            if (req.getStatus() != null) {
                wrapper.lambda().eq(AiKbVersionEntity::getStatus, req.getStatus());
            }
            if (Boolean.TRUE.equals(req.getOrderByVersionNoDesc())) {
                wrapper.lambda().orderByDesc(AiKbVersionEntity::getVersionNo, AiKbVersionEntity::getId);
            } else {
                wrapper.lambda().orderByDesc(AiKbVersionEntity::getUpdateTime, AiKbVersionEntity::getId);
            }
        }
        return wrapper;
    }
}
