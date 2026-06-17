package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbDocumentContentConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbDocumentContentEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbDocumentContentQueryRequest;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbDocumentContentMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentContentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiKbDocumentContentServiceImpl
        extends BaseMapperService<AiKbDocumentContentEntity, AiKbDocumentContentMapper, AiKbDocumentContentDTO>
        implements AiKbDocumentContentService {

    private final AiKbDocumentContentConvert convert;

    public AiKbDocumentContentServiceImpl(AiKbDocumentContentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbDocumentContentEntity, AiKbDocumentContentDTO> convert() {
        return convert;
    }

    public AiKbDocumentContentDTO newDTO() {
        return new AiKbDocumentContentDTO();
    }

    public AiKbDocumentContentEntity newEntity() {
        return new AiKbDocumentContentEntity();
    }

    @Override
    public AiKbDocumentContentDTO getByDocumentId(Long documentId) {
        if (documentId == null) {
            return null;
        }
        AiKbDocumentContentQueryRequest query = new AiKbDocumentContentQueryRequest();
        query.setDocumentId(documentId);
        query.setPage(1);
        query.setSize(1);
        List<AiKbDocumentContentDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbDocumentContentEntity> buildQuery(Query query) {
        QueryWrapper<AiKbDocumentContentEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiKbDocumentContentQueryRequest req) {
            if (req.getDocumentId() != null) {
                wrapper.lambda().eq(AiKbDocumentContentEntity::getDocumentId, req.getDocumentId());
            }
            wrapper.lambda().orderByDesc(AiKbDocumentContentEntity::getUpdateTime, AiKbDocumentContentEntity::getId);
        }
        return wrapper;
    }
}
