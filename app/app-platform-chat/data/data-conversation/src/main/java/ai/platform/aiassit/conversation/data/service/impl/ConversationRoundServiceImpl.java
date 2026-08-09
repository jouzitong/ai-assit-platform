package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationRoundConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationRoundEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationRoundMapper;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public List<ConversationRoundDTO> queryRecent(String sessionCode, Long userId, int limit) {
        if (sessionCode == null || sessionCode.isBlank() || userId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ConversationRoundDTO> result = new ArrayList<>(baseMapper.selectList(
                        Wrappers.<ConversationRoundEntity>lambdaQuery()
                                .eq(ConversationRoundEntity::getSessionCode, sessionCode.trim())
                                .eq(ConversationRoundEntity::getUserId, userId)
                                .orderByDesc(ConversationRoundEntity::getId)
                                .last("LIMIT " + safeLimit))
                .stream()
                .map(aiChatRoundConvert::toDTO)
                .toList());
        Collections.reverse(result);
        return result;
    }

    @Override
    public List<ConversationRoundDTO> queryBefore(String sessionCode, Long userId, Long beforeId, int limit) {
        if (sessionCode == null || sessionCode.isBlank() || userId == null || beforeId == null || beforeId <= 0) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 101));
        List<ConversationRoundDTO> result = new ArrayList<>(baseMapper.selectList(
                        Wrappers.<ConversationRoundEntity>lambdaQuery()
                                .eq(ConversationRoundEntity::getSessionCode, sessionCode.trim())
                                .eq(ConversationRoundEntity::getUserId, userId)
                                .lt(ConversationRoundEntity::getId, beforeId)
                                .orderByDesc(ConversationRoundEntity::getId)
                                .last("LIMIT " + safeLimit))
                .stream()
                .map(aiChatRoundConvert::toDTO)
                .toList());
        Collections.reverse(result);
        return result;
    }

    @Override
    public List<ConversationRoundDTO> queryAfter(String sessionCode, Long userId, Long afterId, int limit) {
        if (sessionCode == null || sessionCode.isBlank() || userId == null || afterId == null || afterId <= 0) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 101));
        return baseMapper.selectList(
                        Wrappers.<ConversationRoundEntity>lambdaQuery()
                                .eq(ConversationRoundEntity::getSessionCode, sessionCode.trim())
                                .eq(ConversationRoundEntity::getUserId, userId)
                                .gt(ConversationRoundEntity::getId, afterId)
                                .orderByAsc(ConversationRoundEntity::getId)
                                .last("LIMIT " + safeLimit))
                .stream()
                .map(aiChatRoundConvert::toDTO)
                .toList();
    }

    @Override
    public ConversationRoundDTO queryLatest(String sessionCode, Long userId) {
        List<ConversationRoundDTO> result = queryRecent(sessionCode, userId, 1);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public ConversationRoundDTO queryOwned(String roundCode, String sessionCode, Long userId) {
        if (roundCode == null || roundCode.isBlank() || sessionCode == null || sessionCode.isBlank() || userId == null) {
            return null;
        }
        ConversationRoundEntity entity = baseMapper.selectOne(Wrappers.<ConversationRoundEntity>lambdaQuery()
                .eq(ConversationRoundEntity::getRoundCode, roundCode.trim())
                .eq(ConversationRoundEntity::getSessionCode, sessionCode.trim())
                .eq(ConversationRoundEntity::getUserId, userId)
                .last("LIMIT 1"));
        return entity == null ? null : aiChatRoundConvert.toDTO(entity);
    }

}
