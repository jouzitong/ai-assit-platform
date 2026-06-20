package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbDocumentConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbDocumentEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbDocumentQueryRequest;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbDocumentMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiKbDocumentServiceImpl
        extends BaseMapperService<AiKbDocumentEntity, AiKbDocumentMapper, AiKbDocumentDTO>
        implements AiKbDocumentService {

    private final AiKbDocumentConvert convert;

    public AiKbDocumentServiceImpl(AiKbDocumentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbDocumentEntity, AiKbDocumentDTO> convert() {
        return convert;
    }

    @Override
    public AiKbDocumentDTO getByKbCodeAndDocumentCode(String kbCode, String documentCode) {
        if (!StringUtils.hasText(kbCode) || !StringUtils.hasText(documentCode)) {
            return null;
        }
        AiKbDocumentQueryRequest query = new AiKbDocumentQueryRequest();
        query.setKbCode(kbCode.trim());
        query.setDocumentCode(documentCode.trim());
        query.setPage(1);
        query.setSize(1);
        List<AiKbDocumentDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AiKbDocumentDTO> listByQuery(AiKbDocumentQueryRequest query) {
        AiKbDocumentQueryRequest payload = query == null ? new AiKbDocumentQueryRequest() : query;
        payload.setPage(payload.getPage() == null ? 1 : payload.getPage());
        payload.setSize(payload.getSize() == null ? Integer.MAX_VALUE : payload.getSize());
        return queryAll(payload);
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbDocumentEntity> buildQuery(Query query) {
        QueryWrapper<AiKbDocumentEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiKbDocumentQueryRequest req) {
            if (StringUtils.hasText(req.getKbCode())) {
                wrapper.lambda().eq(AiKbDocumentEntity::getKbCode, req.getKbCode().trim());
            }
            if (StringUtils.hasText(req.getDocumentCode())) {
                wrapper.lambda().eq(AiKbDocumentEntity::getDocumentCode, req.getDocumentCode().trim());
            }
            if (req.getKbVersionId() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getKbVersionId, req.getKbVersionId());
            }
            if (req.getStatus() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getStatus, req.getStatus());
            }
            if (req.getReviewStatus() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getReviewStatus, req.getReviewStatus());
            }
            wrapper.lambda().orderByDesc(AiKbDocumentEntity::getUpdateTime, AiKbDocumentEntity::getId);
        }
        return wrapper;
    }
}
