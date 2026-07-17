package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationArtifactConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationArtifactEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationArtifactMapper;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class ConversationArtifactServiceImpl
        extends BaseMapperService<ConversationArtifactEntity, ConversationArtifactMapper, ConversationArtifactDTO>
        implements ConversationArtifactService {

    private final ConversationArtifactConvert aiChatArtifactConvert;

    public ConversationArtifactServiceImpl(ConversationArtifactConvert aiChatArtifactConvert) {
        this.aiChatArtifactConvert = aiChatArtifactConvert;
    }

    @Override
    protected IConvert<ConversationArtifactEntity, ConversationArtifactDTO> convert() {
        return aiChatArtifactConvert;
    }

}
