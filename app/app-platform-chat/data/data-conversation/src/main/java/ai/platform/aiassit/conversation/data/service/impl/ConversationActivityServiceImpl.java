package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationActivityConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationActivityEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.mapper.ConversationActivityMapper;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

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
}
