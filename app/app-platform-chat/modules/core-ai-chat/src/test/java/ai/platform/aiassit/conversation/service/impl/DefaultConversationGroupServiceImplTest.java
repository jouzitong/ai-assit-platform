package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationGroupDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.data.service.ConversationGroupDataService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupAssignRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationGroupDeleteRequest;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationGroupServiceImplTest {

    private ConversationGroupDataService groupDataService;
    private ConversationSessionService sessionService;
    private DefaultConversationGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        groupDataService = mock(ConversationGroupDataService.class);
        sessionService = mock(ConversationSessionService.class);
        service = new DefaultConversationGroupServiceImpl(groupDataService, sessionService);
    }

    @Test
    void createGroupTrimsNameAndBindsCurrentUser() {
        when(groupDataService.add(any(ConversationGroupDTO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ConversationGroupCreateRequest request = new ConversationGroupCreateRequest();
        request.setGroupName("  数据分析  ");

        ConversationGroupDTO created = service.createGroup(7L, request);

        assertThat(created.getUserId()).isEqualTo(7L);
        assertThat(created.getGroupName()).isEqualTo("数据分析");
        assertThat(created.getGroupCode()).startsWith("group-");
        verify(groupDataService).add(any(ConversationGroupDTO.class));
    }

    @Test
    void rejectsBlankAndOverlongGroupNames() {
        ConversationGroupCreateRequest blank = new ConversationGroupCreateRequest();
        blank.setGroupName("  ");
        assertThatThrownBy(() -> service.createGroup(7L, blank))
                .isInstanceOf(BizException.class);

        ConversationGroupCreateRequest overlong = new ConversationGroupCreateRequest();
        overlong.setGroupName("a".repeat(129));
        assertThatThrownBy(() -> service.createGroup(7L, overlong))
                .isInstanceOf(BizException.class);
    }

    @Test
    void deleteGroupClearsSessionAssociationsBeforeSoftDeletingGroup() {
        ConversationGroupDTO group = group("group-1", 7L);
        group.setId(11L);
        when(groupDataService.getByUserIdAndCode(7L, "group-1")).thenReturn(group);
        when(sessionService.clearGroupCodeByUserAndGroup(7L, "group-1")).thenReturn(2);
        when(groupDataService.delete(11L)).thenReturn(true);
        ConversationGroupDeleteRequest request = new ConversationGroupDeleteRequest();
        request.setGroupCode(" group-1 ");

        assertThat(service.deleteGroup(7L, request)).isTrue();

        InOrder order = inOrder(sessionService, groupDataService);
        order.verify(sessionService).clearGroupCodeByUserAndGroup(7L, "group-1");
        order.verify(groupDataService).delete(11L);
    }

    @Test
    void assignSessionRequiresBothSessionAndTargetGroupToBelongToUser() {
        ConversationSessionDTO session = session("session-1", 7L, ConversationBusinessType.CUSTOM);
        when(sessionService.get(any(ConversationHistoryQueryRequest.class)))
                .thenReturn(session);
        when(groupDataService.getByUserIdAndCode(7L, "group-2")).thenReturn(null);
        ConversationGroupAssignRequest request = new ConversationGroupAssignRequest();
        request.setSessionCode("session-1");
        request.setGroupCode("group-2");

        assertThatThrownBy(() -> service.assignSession(7L, request))
                .isInstanceOf(BizException.class);

        verify(sessionService, never()).updateGroupCode(any(), any(), any());
    }

    @Test
    void pageAssistantSessionCannotBeAssignedToRegularGroup() {
        ConversationSessionDTO session = session("settings-session", 7L, ConversationBusinessType.PAGE_ASSISTANT);
        when(sessionService.get(any(ConversationHistoryQueryRequest.class)))
                .thenReturn(session);
        ConversationGroupAssignRequest request = new ConversationGroupAssignRequest();
        request.setSessionCode("settings-session");
        request.setGroupCode("group-1");

        assertThatThrownBy(() -> service.assignSession(7L, request))
                .isInstanceOf(BizException.class);

        verify(groupDataService, never()).getByUserIdAndCode(7L, "group-1");
        verify(sessionService, never()).updateGroupCode(any(), any(), any());
    }

    @Test
    void existingSessionKeepsPersistedGroupWhenRequestOmitsItAndRejectsMismatch() {
        ConversationSessionDTO session = session("session-1", 7L, ConversationBusinessType.CUSTOM);
        session.setGroupCode("group-1");
        ConversationGroupDTO group = group("group-1", 7L);
        when(groupDataService.getByUserIdAndCode(7L, "group-1")).thenReturn(group);

        service.validateExistingSessionGroup(7L, session, null, ConversationBusinessType.CUSTOM);
        service.validateExistingSessionGroup(7L, session, " group-1 ", ConversationBusinessType.CUSTOM);

        assertThatThrownBy(() -> service.validateExistingSessionGroup(
                7L, session, "group-2", ConversationBusinessType.CUSTOM))
                .isInstanceOf(BizException.class);
    }

    private ConversationGroupDTO group(String groupCode, Long userId) {
        ConversationGroupDTO group = new ConversationGroupDTO();
        group.setGroupCode(groupCode);
        group.setUserId(userId);
        group.setGroupName("分组");
        return group;
    }

    private ConversationSessionDTO session(String sessionCode,
                                           Long userId,
                                           ConversationBusinessType businessType) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode(sessionCode);
        session.setUserId(userId);
        session.setBusinessType(businessType);
        return session;
    }
}
