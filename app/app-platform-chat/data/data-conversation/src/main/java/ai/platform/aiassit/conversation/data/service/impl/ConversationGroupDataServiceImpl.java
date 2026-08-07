package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.convert.ConversationGroupConvert;
import ai.platform.aiassit.conversation.data.entity.ConversationGroupEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationGroupQueryRequest;
import ai.platform.aiassit.conversation.data.mapper.ConversationGroupMapper;
import ai.platform.aiassit.conversation.data.service.ConversationGroupDataService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 分组基础数据服务实现。 */
@Service
public class ConversationGroupDataServiceImpl
        extends BaseMapperService<ConversationGroupEntity, ConversationGroupMapper, ConversationGroupDTO>
        implements ConversationGroupDataService {

    private final ConversationGroupConvert convert;

    public ConversationGroupDataServiceImpl(ConversationGroupConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<ConversationGroupEntity, ConversationGroupDTO> convert() {
        return convert;
    }

    @Override
    public List<ConversationGroupDTO> listByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        ConversationGroupQueryRequest query = new ConversationGroupQueryRequest();
        query.setUserId(userId);
        query.setPage(1);
        query.setSize(Integer.MAX_VALUE);
        return queryAll(query);
    }

    @Override
    public ConversationGroupDTO getByUserIdAndCode(Long userId, String groupCode) {
        if (userId == null || !StringUtils.hasText(groupCode)) {
            return null;
        }
        ConversationGroupQueryRequest query = new ConversationGroupQueryRequest();
        query.setUserId(userId);
        query.setGroupCode(groupCode.trim());
        query.setPage(1);
        query.setSize(2);
        return get(query);
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<ConversationGroupEntity> buildQuery(Query query) {
        QueryWrapper<ConversationGroupEntity> wrapper = super.buildQuery(query);
        if (query instanceof ConversationGroupQueryRequest request) {
            if (request.getUserId() != null) {
                wrapper.eq("user_id", request.getUserId());
            }
            if (StringUtils.hasText(request.getGroupCode())) {
                wrapper.eq("group_code", request.getGroupCode().trim());
            }
            wrapper.orderByDesc("update_time").orderByDesc("id");
        }
        return wrapper;
    }
}
