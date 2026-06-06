package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatSessionConvert;
import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.mapper.AiChatSessionMapper;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatSessionServiceImpl
        extends BaseMapperService<AiChatSessionEntity, AiChatSessionMapper, AiChatSessionDTO>
        implements AiChatSessionService {

    private final AiChatSessionConvert aiChatSessionConvert;

    public AiChatSessionServiceImpl(AiChatSessionConvert aiChatSessionConvert) {
        this.aiChatSessionConvert = aiChatSessionConvert;
    }

    @Override
    protected IConvert<AiChatSessionEntity, AiChatSessionDTO> convert() {
        return aiChatSessionConvert;
    }

    public AiChatSessionDTO newDTO() {
        return new AiChatSessionDTO();
    }

    public AiChatSessionEntity newEntity() {
        return new AiChatSessionEntity();
    }

//    @Override
//    protected <Query extends BaseRequest> QueryWrapper<AiChatSessionEntity> buildQuery(Query query) {
//        QueryWrapper<AiChatSessionEntity> wrapper = super.buildQuery(query);
//        if (query instanceof AiChatHistoryQueryRequest req) {
//            if (StringUtils.hasText(req.getSessionCode())) {
//                wrapper.lambda().eq(AiChatSessionEntity::getSessionCode, req.getSessionCode());
//            }
//            if (req.getUserId() != null) {
//                wrapper.lambda().eq(AiChatSessionEntity::getUserId, req.getUserId());
//            }
//            if (req.getBusinessType() != null) {
//                wrapper.lambda().eq(AiChatSessionEntity::getBusinessType, req.getBusinessType());
//            }
//            wrapper.lambda().orderByDesc(AiChatSessionEntity::getPinned, AiChatSessionEntity::getUpdateTime, AiChatSessionEntity::getId);
//        }
//        return wrapper;
//    }
}
