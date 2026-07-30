package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.knowledge.manage.convert.AiKbDocumentConvert;
import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentDTO;
import ai.platform.aiassit.knowledge.manage.entity.document.req.AiKbDocumentQueryRequest;
import ai.platform.aiassit.knowledge.manage.mapper.AiKbDocumentMapper;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentService;
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
            if (req.getId() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getId, req.getId());
            }
            if (StringUtils.hasText(req.getDocumentCode())) {
                wrapper.lambda().eq(AiKbDocumentEntity::getDocumentCode, req.getDocumentCode().trim());
            }
            if (req.getDocumentCodes() != null && !req.getDocumentCodes().isEmpty()) {
                List<String> documentCodes = req.getDocumentCodes().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
                if (!documentCodes.isEmpty()) {
                    wrapper.lambda().in(AiKbDocumentEntity::getDocumentCode, documentCodes);
                }
            }
            if (StringUtils.hasText(req.getKeyword())) {
                String keyword = req.getKeyword().trim();
                wrapper.and(w -> w.lambda()
                        .like(AiKbDocumentEntity::getKbCode, keyword)
                        .or()
                        .like(AiKbDocumentEntity::getDocumentCode, keyword)
                        .or()
                        .like(AiKbDocumentEntity::getDocumentName, keyword)
                        .or()
                        .like(AiKbDocumentEntity::getBizKey, keyword)
                        .or()
                        .like(AiKbDocumentEntity::getProviderDocumentId, keyword));
            }
            if (req.getBizType() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getBizType, req.getBizType());
            }
            if (req.getStatus() != null) {
                wrapper.lambda().eq(AiKbDocumentEntity::getStatus, req.getStatus());
            }
            wrapper.lambda().orderByDesc(AiKbDocumentEntity::getUpdateTime, AiKbDocumentEntity::getId);
        }
        return wrapper;
    }
}
