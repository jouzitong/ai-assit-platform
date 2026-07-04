package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatArtifactConvert;
import ai.platform.aiassit.chat.history.entity.AiChatArtifactEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.mapper.AiChatArtifactMapper;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatArtifactServiceImpl
        extends BaseMapperService<AiChatArtifactEntity, AiChatArtifactMapper, AiChatArtifactDTO>
        implements AiChatArtifactService {

    private final AiChatArtifactConvert aiChatArtifactConvert;

    public AiChatArtifactServiceImpl(AiChatArtifactConvert aiChatArtifactConvert) {
        this.aiChatArtifactConvert = aiChatArtifactConvert;
    }

    @Override
    protected IConvert<AiChatArtifactEntity, AiChatArtifactDTO> convert() {
        return aiChatArtifactConvert;
    }

}
