package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationRoundConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationRoundEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationRoundMapper;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class ConversationRoundServiceImpl
        extends BaseMapperService<ConversationRoundEntity, ConversationRoundMapper, ConversationRoundDTO>
        implements ConversationRoundService {

    private final ConversationRoundConvert aiChatRoundConvert;

    public ConversationRoundServiceImpl(ConversationRoundConvert aiChatRoundConvert) {
        this.aiChatRoundConvert = aiChatRoundConvert;
    }

    @Override
    protected IConvert<ConversationRoundEntity, ConversationRoundDTO> convert() {
        return aiChatRoundConvert;
    }

}
