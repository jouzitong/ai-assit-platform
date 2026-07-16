package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatSessionServiceImplTest {

    @Test
    void sessionQueryAppliesIgnoredSharedUserIdAsOwnerCondition() {
        AiChatSessionServiceImpl service = new AiChatSessionServiceImpl(null);
        AiChatHistoryQueryRequest request = new AiChatHistoryQueryRequest();
        request.setSessionCode("session-1");
        request.setUserId(7L);

        QueryWrapper<?> wrapper = service.buildQuery(request);

        assertThat(wrapper.getSqlSegment()).contains("session_code", "user_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(7L);
    }

    @Test
    void sessionQueryDoesNotInventOwnerWhenCallerHasNoAuthenticatedUser() {
        AiChatSessionServiceImpl service = new AiChatSessionServiceImpl(null);
        AiChatHistoryQueryRequest request = new AiChatHistoryQueryRequest();
        request.setSessionCode("session-1");

        QueryWrapper<?> wrapper = service.buildQuery(request);

        assertThat(wrapper.getSqlSegment()).contains("session_code").doesNotContain("user_id");
    }
}
