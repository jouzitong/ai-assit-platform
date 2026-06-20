package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import ai.platform.aiassist.service.ai.kb.convert.AiKbStoreConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbStoreEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbStoreDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbStoreQueryRequest;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbStoreMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbStoreService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiKbStoreServiceImpl
        extends BaseMapperService<AiKbStoreEntity, AiKbStoreMapper, AiKbStoreDTO>
        implements AiKbStoreService {

    private final AiKbStoreConvert convert;

    public AiKbStoreServiceImpl(AiKbStoreConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbStoreEntity, AiKbStoreDTO> convert() {
        return convert;
    }

    public AiKbStoreDTO newDTO() {
        return new AiKbStoreDTO();
    }

    public AiKbStoreEntity newEntity() {
        return new AiKbStoreEntity();
    }

    @Override
    public AiKbStoreDTO getByKbCode(String kbCode) {
        if (!StringUtils.hasText(kbCode)) {
            return null;
        }
        AiKbStoreQueryRequest query = new AiKbStoreQueryRequest();
        query.setKbCode(kbCode.trim());
        query.setPage(1);
        query.setSize(1);
        List<AiKbStoreDTO> list = queryAll(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AiKbStoreDTO> list(AiKbListRequest request) {
        AiKbStoreQueryRequest query = new AiKbStoreQueryRequest();
        if (request != null) {
            query.setEnabled(request.getEnabled());
            if (request.getSourceType() != null) {
                query.setBizType(sourceTypeToBizType(request.getSourceType()));
            }
        }
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        return queryAll(query);
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbStoreEntity> buildQuery(Query query) {
        QueryWrapper<AiKbStoreEntity> wrapper = new QueryWrapper<>();
        if (query instanceof AiKbStoreQueryRequest req) {
            if (StringUtils.hasText(req.getKbCode())) {
                wrapper.lambda().eq(AiKbStoreEntity::getKbCode, req.getKbCode().trim());
            }
            if (StringUtils.hasText(req.getKbName())) {
                wrapper.lambda().like(AiKbStoreEntity::getKbName, req.getKbName().trim());
            }
            if (req.getBizType() != null) {
                wrapper.lambda().eq(AiKbStoreEntity::getBizType, req.getBizType());
            }
            if (req.getStatus() != null) {
                wrapper.lambda().eq(AiKbStoreEntity::getStatus, req.getStatus());
            }
            if (req.getEnabled() != null) {
                if (Boolean.TRUE.equals(req.getEnabled())) {
                    wrapper.lambda().ne(AiKbStoreEntity::getStatus, AiKbStoreStatus.DISABLED);
                } else {
                    wrapper.lambda().eq(AiKbStoreEntity::getStatus, AiKbStoreStatus.DISABLED);
                }
            }
            if (StringUtils.hasText(req.getKeyword())) {
                String keyword = req.getKeyword().trim();
                wrapper.and(item -> item.lambda()
                        .like(AiKbStoreEntity::getKbCode, keyword)
                        .or()
                        .like(AiKbStoreEntity::getKbName, keyword)
                        .or()
                        .like(AiKbStoreEntity::getProviderKbId, keyword));
            }
            wrapper.lambda().orderByDesc(AiKbStoreEntity::getUpdateTime, AiKbStoreEntity::getId);
        }
        return wrapper;
    }

    private ai.platform.aiassist.service.ai.api.enums.AiKbBizType sourceTypeToBizType(AiKbSourceType sourceType) {
        return sourceType == null ? null : ai.platform.aiassist.service.ai.api.enums.AiKbBizType.valueOf(sourceType.name());
    }
}
