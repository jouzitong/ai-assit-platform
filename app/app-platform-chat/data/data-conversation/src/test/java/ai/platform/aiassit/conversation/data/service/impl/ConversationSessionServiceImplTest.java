package ai.platform.aiassit.conversation.data.service.impl;

import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSessionServiceImplTest {

    @Test
    void sessionQueryAppliesIgnoredSharedUserIdAsOwnerCondition() {
        ConversationSessionServiceImpl service = new ConversationSessionServiceImpl(null);
        ConversationHistoryQueryRequest request = new ConversationHistoryQueryRequest();
        request.setSessionCode("session-1");
        request.setUserId(7L);

        QueryWrapper<?> wrapper = service.buildQuery(request);

        assertThat(wrapper.getSqlSegment()).contains("session_code", "user_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(7L);
    }

    @Test
    void sessionQueryDoesNotInventOwnerWhenCallerHasNoAuthenticatedUser() {
        ConversationSessionServiceImpl service = new ConversationSessionServiceImpl(null);
        ConversationHistoryQueryRequest request = new ConversationHistoryQueryRequest();
        request.setSessionCode("session-1");

        QueryWrapper<?> wrapper = service.buildQuery(request);

        assertThat(wrapper.getSqlSegment()).contains("session_code").doesNotContain("user_id");
    }
}
