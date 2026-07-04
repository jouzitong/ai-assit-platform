package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatRoundConvert;
import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.mapper.AiChatRoundMapper;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

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

}
