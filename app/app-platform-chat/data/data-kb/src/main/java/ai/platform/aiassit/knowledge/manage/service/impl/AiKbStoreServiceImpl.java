package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKbStoreSyncStatus;
import ai.platform.aiassit.knowledge.manage.convert.AiKbStoreConvert;
import ai.platform.aiassit.knowledge.manage.entity.store.AiKbStoreEntity;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.knowledge.manage.mapper.AiKbStoreMapper;
import ai.platform.aiassit.knowledge.manage.service.AiKbStoreService;
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
        }
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        return queryAll(query).stream()
                .filter(this::isSyncedStore)
                .toList();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiKbStoreEntity> buildQuery(Query query) {
        QueryWrapper<AiKbStoreEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiKbStoreQueryRequest req) {
            if (req.getEnabled() != null) {
                wrapper.lambda().eq(AiKbStoreEntity::getEnabled, req.getEnabled());
            }
            if (StringUtils.hasText(req.getKeyword())) {
                String keyword = req.getKeyword().trim();
                wrapper.and(item -> item.lambda()
                        .like(AiKbStoreEntity::getKbCode, keyword)
                        .or()
                        .like(AiKbStoreEntity::getKbName, keyword)
                        .or()
                        .like(AiKbStoreEntity::getProviderKbId, keyword)
                        .or()
                        .like(AiKbStoreEntity::getDescription, keyword));
            }
            wrapper.lambda().orderByDesc(AiKbStoreEntity::getUpdateTime, AiKbStoreEntity::getId);
        }
        return wrapper;
    }

    private boolean isSyncedStore(AiKbStoreDTO store) {
        return store.getSyncStatus() == null || store.getSyncStatus() == AiKbStoreSyncStatus.ACTIVE;
    }

}
