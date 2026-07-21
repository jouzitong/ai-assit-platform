package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationArtifactConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationArtifactEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationArtifactMapper;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

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

    @Override
    public List<ConversationArtifactDTO> queryByRoundCodes(Collection<String> roundCodes) {
        if (roundCodes == null || roundCodes.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(Wrappers.<ConversationArtifactEntity>lambdaQuery()
                        .in(ConversationArtifactEntity::getRoundCode, roundCodes)
                        .orderByAsc(ConversationArtifactEntity::getRoundCode)
                        .orderByAsc(ConversationArtifactEntity::getSeqNo))
                .stream()
                .map(aiChatArtifactConvert::toDTO)
                .toList();
    }

}
