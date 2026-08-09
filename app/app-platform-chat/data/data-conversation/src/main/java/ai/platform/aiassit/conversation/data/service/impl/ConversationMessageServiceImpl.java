package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationMessageConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationMessageEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationMessageMapper;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

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

    @Override
    public List<ConversationMessageDTO> queryByRoundCodes(Collection<String> roundCodes) {
        if (roundCodes == null || roundCodes.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(Wrappers.<ConversationMessageEntity>lambdaQuery()
                        .in(ConversationMessageEntity::getRoundCode, roundCodes)
                        .orderByAsc(ConversationMessageEntity::getId))
                .stream()
                .map(aiChatMessageConvert::toDTO)
                .toList();
    }

    @Override
    public List<ConversationMessageDTO> queryByRoundCode(String roundCode) {
        if (roundCode == null || roundCode.isBlank()) {
            return List.of();
        }
        return baseMapper.selectList(Wrappers.<ConversationMessageEntity>lambdaQuery()
                        .eq(ConversationMessageEntity::getRoundCode, roundCode.trim())
                        .orderByAsc(ConversationMessageEntity::getId))
                .stream()
                .map(aiChatMessageConvert::toDTO)
                .toList();
    }

}
