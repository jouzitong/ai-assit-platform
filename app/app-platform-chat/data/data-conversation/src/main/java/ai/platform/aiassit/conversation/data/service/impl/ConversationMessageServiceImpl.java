package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationMessageConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationMessageEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationMessageMapper;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class ConversationMessageServiceImpl
        extends BaseMapperService<ConversationMessageEntity, ConversationMessageMapper, ConversationMessageDTO>
        implements ConversationMessageService {

    private final ConversationMessageConvert aiChatMessageConvert;

    public ConversationMessageServiceImpl(ConversationMessageConvert aiChatMessageConvert) {
        this.aiChatMessageConvert = aiChatMessageConvert;
    }

    @Override
    protected IConvert<ConversationMessageEntity, ConversationMessageDTO> convert() {
        return aiChatMessageConvert;
    }

}
