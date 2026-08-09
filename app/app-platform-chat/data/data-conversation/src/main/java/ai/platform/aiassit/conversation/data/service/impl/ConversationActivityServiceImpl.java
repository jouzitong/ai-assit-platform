package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationActivityConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationActivityEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationActivityMapper;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class ConversationActivityServiceImpl
        extends BaseMapperService<ConversationActivityEntity, ConversationActivityMapper, ConversationActivityDTO>
        implements ConversationActivityService {

    private final ConversationActivityConvert activityConvert;

    public ConversationActivityServiceImpl(ConversationActivityConvert activityConvert) {
        this.activityConvert = activityConvert;
    }

    @Override
    protected IConvert<ConversationActivityEntity, ConversationActivityDTO> convert() {
        return activityConvert;
    }

    @Override
    public List<ConversationActivityDTO> queryByRoundCodes(
            String sessionCode, Long userId, Collection<String> roundCodes) {
        if (sessionCode == null || sessionCode.isBlank() || userId == null
                || roundCodes == null || roundCodes.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(Wrappers.<ConversationActivityEntity>lambdaQuery()
                        .eq(ConversationActivityEntity::getSessionCode, sessionCode.trim())
                        .eq(ConversationActivityEntity::getUserId, userId)
                        .in(ConversationActivityEntity::getRoundCode, roundCodes)
                        .orderByAsc(ConversationActivityEntity::getRoundCode)
                        .orderByAsc(ConversationActivityEntity::getSeqNo))
                .stream()
                .map(activityConvert::toDTO)
                .toList();
    }
}
