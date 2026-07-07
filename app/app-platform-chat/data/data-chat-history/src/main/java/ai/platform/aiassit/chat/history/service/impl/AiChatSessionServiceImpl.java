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

}
