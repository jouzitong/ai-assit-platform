package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.knowledge.manage.convert.AiKbDocumentVersionConvert;
import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentVersionEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentVersionDTO;
import ai.platform.aiassit.knowledge.manage.entity.document.req.AiKbDocumentVersionQueryRequest;
import ai.platform.aiassit.knowledge.manage.mapper.AiKbDocumentVersionMapper;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiKbDocumentVersionServiceImpl
        extends BaseMapperService<AiKbDocumentVersionEntity, AiKbDocumentVersionMapper, AiKbDocumentVersionDTO>
        implements AiKbDocumentVersionService {

    private final AiKbDocumentVersionConvert convert;

    public AiKbDocumentVersionServiceImpl(AiKbDocumentVersionConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbDocumentVersionEntity, AiKbDocumentVersionDTO> convert() {
        return convert;
    }

    public AiKbDocumentVersionDTO newDTO() {
        return new AiKbDocumentVersionDTO();
    }

    public AiKbDocumentVersionEntity newEntity() {
        return new AiKbDocumentVersionEntity();
    }

    @Override
    public List<AiKbDocumentVersionDTO> listByQuery(AiKbDocumentVersionQueryRequest query) {
        AiKbDocumentVersionQueryRequest payload = query == null ? new AiKbDocumentVersionQueryRequest() : query;
        payload.setPage(payload.getPage() == null ? 1 : payload.getPage());
        payload.setSize(payload.getSize() == null ? Integer.MAX_VALUE : payload.getSize());
        return queryAll(payload);
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbDocumentVersionEntity> buildQuery(Query query) {
        QueryWrapper<AiKbDocumentVersionEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiKbDocumentVersionQueryRequest req) {
            if (StringUtils.hasText(req.getKbCode())) {
                wrapper.lambda().eq(AiKbDocumentVersionEntity::getKbCode, req.getKbCode().trim());
            }
            if (StringUtils.hasText(req.getDocumentCode())) {
                wrapper.lambda().eq(AiKbDocumentVersionEntity::getDocumentCode, req.getDocumentCode().trim());
            }
            if (req.getDocumentVersionNo() != null) {
                wrapper.lambda().eq(AiKbDocumentVersionEntity::getDocumentVersionNo, req.getDocumentVersionNo());
            }
            wrapper.lambda().orderByDesc(AiKbDocumentVersionEntity::getUpdateTime, AiKbDocumentVersionEntity::getId);
        }
        return wrapper;
    }
}
