package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatRoundConvert;
import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.mapper.AiChatRoundMapper;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatRoundServiceImpl
        extends BaseMapperService<AiChatRoundEntity, AiChatRoundMapper, AiChatRoundDTO>
        implements AiChatRoundService {

    private final AiChatRoundConvert aiChatRoundConvert;

    public AiChatRoundServiceImpl(AiChatRoundConvert aiChatRoundConvert) {
        this.aiChatRoundConvert = aiChatRoundConvert;
    }

    @Override
    protected IConvert<AiChatRoundEntity, AiChatRoundDTO> convert() {
        return aiChatRoundConvert;
    }

    public AiChatRoundDTO newDTO() {
        return new AiChatRoundDTO();
    }

    public AiChatRoundEntity newEntity() {
        return new AiChatRoundEntity();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiChatRoundEntity> buildQuery(Query query) {
        QueryWrapper<AiChatRoundEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiChatHistoryQueryRequest req) {
            if (StringUtils.hasText(req.getSessionCode())) {
                wrapper.lambda().eq(AiChatRoundEntity::getSessionCode, req.getSessionCode());
            }
            if (StringUtils.hasText(req.getRoundCode())) {
                wrapper.lambda().eq(AiChatRoundEntity::getRoundCode, req.getRoundCode());
            }
            if (req.getUserId() != null) {
                wrapper.lambda().eq(AiChatRoundEntity::getUserId, req.getUserId());
            }
            wrapper.lambda().orderByAsc(AiChatRoundEntity::getId);
        }
        return wrapper;
    }
}
