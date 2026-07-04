package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatMessageConvert;
import ai.platform.aiassit.chat.history.entity.AiChatMessageEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.mapper.AiChatMessageMapper;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatMessageServiceImpl
        extends BaseMapperService<AiChatMessageEntity, AiChatMessageMapper, AiChatMessageDTO>
        implements AiChatMessageService {

    private final AiChatMessageConvert aiChatMessageConvert;

    public AiChatMessageServiceImpl(AiChatMessageConvert aiChatMessageConvert) {
        this.aiChatMessageConvert = aiChatMessageConvert;
    }

    @Override
    protected IConvert<AiChatMessageEntity, AiChatMessageDTO> convert() {
        return aiChatMessageConvert;
    }

}
