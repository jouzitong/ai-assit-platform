package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatSessionConvert;
import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.mapper.AiChatSessionMapper;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
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

    /**
     * Session ownership is a session-table concern. The shared history request deliberately
     * ignores {@code userId} in the generic query builder because message history has no
     * {@code user_id} column, so enforce it explicitly for session queries here.
     */
    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiChatSessionEntity> buildQuery(Query query) {
        QueryWrapper<AiChatSessionEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiChatHistoryQueryRequest request && request.getUserId() != null) {
            wrapper.eq("user_id", request.getUserId());
        }
        return wrapper;
    }

}
