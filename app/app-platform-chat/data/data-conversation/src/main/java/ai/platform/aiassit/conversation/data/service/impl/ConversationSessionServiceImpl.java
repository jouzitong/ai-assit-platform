package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationSessionConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationSessionEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.mapper.ConversationSessionMapper;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class ConversationSessionServiceImpl
        extends BaseMapperService<ConversationSessionEntity, ConversationSessionMapper, ConversationSessionDTO>
        implements ConversationSessionService {

    private final ConversationSessionConvert aiChatSessionConvert;

    public ConversationSessionServiceImpl(ConversationSessionConvert aiChatSessionConvert) {
        this.aiChatSessionConvert = aiChatSessionConvert;
    }

    @Override
    protected IConvert<ConversationSessionEntity, ConversationSessionDTO> convert() {
        return aiChatSessionConvert;
    }

    /**
     * Session ownership is a session-table concern. The shared history request deliberately
     * ignores {@code userId} in the generic query builder because message history has no
     * {@code user_id} column, so enforce it explicitly for session queries here.
     */
    @Override
    protected <Query extends BaseRequest> QueryWrapper<ConversationSessionEntity> buildQuery(Query query) {
        QueryWrapper<ConversationSessionEntity> wrapper = super.buildQuery(query);
        if (query instanceof ConversationHistoryQueryRequest request && request.getUserId() != null) {
            wrapper.eq("user_id", request.getUserId());
        }
        return wrapper;
    }

}
