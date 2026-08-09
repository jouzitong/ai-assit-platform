package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryPageResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryWindowResponse;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHistoryServiceImplTest {

    private final ConversationSessionService sessionService = mock(ConversationSessionService.class);
    private final ConversationRoundService roundService = mock(ConversationRoundService.class);
    private final ConversationMessageService messageService = mock(ConversationMessageService.class);
    private final ConversationArtifactService artifactService = mock(ConversationArtifactService.class);
    private final ConversationActivityService activityService = mock(ConversationActivityService.class);
    private final ConversationHistoryCursorCodec cursorCodec = new ConversationHistoryCursorCodec();
    private final ConversationHistoryServiceImpl service = new ConversationHistoryServiceImpl(
            sessionService, roundService, messageService, artifactService, activityService, cursorCodec);

    @BeforeEach
    void ownsSession() {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode("session-1");
        session.setUserId(42L);
        when(sessionService.get(any(ConversationHistoryQueryRequest.class))).thenReturn(session);
    }

    @Test
    void latestPageDropsOnlyTheLookaheadRoundAndBuildsBeforeCursor() {
        when(roundService.queryRecent("session-1", 42L, 3))
                .thenReturn(List.of(round(1), round(2), round(3)));

        ConversationHistoryPageResponse response = service.page(
                "tenant-a", 42L, " session-1 ", null, 2);

        assertThat(roundIds(response)).containsExactly(2L, 3L);
        assertThat(response.isHasMore()).isTrue();
        assertThat(cursorCodec.decode(response.getNextCursor(), "session-1", 42L)).isEqualTo(2L);
        verify(roundService).queryRecent("session-1", 42L, 3);
    }

    @Test
    void beforePageUsesTheOwnedCursorWithoutRepeatingItsRound() {
        String cursor = cursorCodec.encode("session-1", 42L, 10L);
        when(roundService.queryBefore("session-1", 42L, 10L, 3))
                .thenReturn(List.of(round(6), round(7), round(8)));

        ConversationHistoryPageResponse response = service.page(
                "tenant-a", 42L, "session-1", cursor, 2);

        assertThat(roundIds(response)).containsExactly(7L, 8L);
        assertThat(cursorCodec.decode(response.getNextCursor(), "session-1", 42L)).isEqualTo(7L);
        verify(roundService).queryBefore("session-1", 42L, 10L, 3);
    }

    @Test
    void rejectsMalformedCrossSessionAndCrossUserCursorsBeforeReadingRounds() {
        String crossSession = cursorCodec.encode("session-2", 42L, 10L);
        String crossUser = cursorCodec.encode("session-1", 43L, 10L);

        assertInvalidCursor("broken-cursor");
        assertInvalidCursor(crossSession);
        assertInvalidCursor(crossUser);
        verify(roundService, never()).queryBefore(any(), any(), any(), any(Integer.class));
        verify(roundService, never()).queryRecent(any(), any(), any(Integer.class));
    }

    @Test
    void rejectsAConversationThatIsNotOwnedByTheCurrentUser() {
        when(sessionService.get(any(ConversationHistoryQueryRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> service.page("tenant-a", 99L, "session-1", null, 20))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND));
        verify(roundService, never()).queryRecent(any(), any(), any(Integer.class));
    }

    @Test
    void sourceWindowReturnsBoundedRoundsAndBothContinuationCursors() {
        ConversationRoundDTO center = round(20);
        when(roundService.queryOwned("round-20", "session-1", 42L)).thenReturn(center);
        when(roundService.queryBefore("session-1", 42L, 20L, 11)).thenReturn(rounds(9, 19));
        when(roundService.queryAfter("session-1", 42L, 20L, 11)).thenReturn(rounds(21, 31));

        ConversationHistoryWindowResponse response = service.window(
                "tenant-a", 42L, "session-1", "round-20");

        assertThat(windowRoundIds(response)).containsExactlyElementsOf(
                LongStream.rangeClosed(10, 30).boxed().toList());
        assertThat(response.isHasEarlier()).isTrue();
        assertThat(response.isHasLater()).isTrue();
        assertThat(cursorCodec.decode(response.getBeforeCursor(), "session-1", 42L)).isEqualTo(10L);
        assertThat(cursorCodec.decode(response.getAfterCursor(), "session-1", 42L)).isEqualTo(30L);
    }

    @Test
    void sourceWindowAtBothEdgesHasNoContinuationCursor() {
        ConversationRoundDTO center = round(1);
        when(roundService.queryOwned("round-1", "session-1", 42L)).thenReturn(center);
        when(roundService.queryBefore("session-1", 42L, 1L, 11)).thenReturn(List.of());
        when(roundService.queryAfter("session-1", 42L, 1L, 11)).thenReturn(List.of());

        ConversationHistoryWindowResponse response = service.window(
                "tenant-a", 42L, "session-1", "round-1");

        assertThat(windowRoundIds(response)).containsExactly(1L);
        assertThat(response.isHasEarlier()).isFalse();
        assertThat(response.isHasLater()).isFalse();
        assertThat(response.getBeforeCursor()).isNull();
        assertThat(response.getAfterCursor()).isNull();
    }

    @Test
    void aggregatesAndSortsMessagesArtifactsAndActivitiesForOnlyThePageRounds() {
        when(roundService.queryRecent("session-1", 42L, 2)).thenReturn(List.of(round(2)));
        when(messageService.queryByRoundCodes(List.of("round-2"))).thenReturn(List.of(
                message("round-2", "assistant", 2, 22),
                message("other-round", "ignored", 0, 1),
                message("round-2", "user", 1, 21)));
        when(artifactService.queryByRoundCodes(List.of("round-2"))).thenReturn(List.of(
                artifact("round-2", "RENDER_JSON", 2),
                artifact("round-2", "TEXT", 1),
                artifact("other-round", "RENDER_JSON", 0)));
        when(activityService.queryByRoundCodes("session-1", 42L, List.of("round-2"))).thenReturn(List.of(
                activity("round-2", "tool-finished", 2),
                activity("other-round", "ignored", 0),
                activity("round-2", "tool-started", 1)));

        ConversationHistoryPageResponse response = service.page(
                "tenant-a", 42L, "session-1", null, 1);

        var detail = response.getRounds().get(0);
        assertThat(detail.getMessages()).extracting(ConversationMessageDTO::getRole)
                .containsExactly("user", "assistant");
        assertThat(detail.getArtifacts()).extracting(ConversationArtifactDTO::getArtifactType)
                .containsExactly("TEXT", "RENDER_JSON");
        assertThat(detail.getActivities()).extracting(ConversationActivityDTO::getActivityName)
                .containsExactly("tool-started", "tool-finished");
        assertThat(detail.getRenderType()).isEqualTo("TODO");
        verify(activityService).queryByRoundCodes("session-1", 42L, List.of("round-2"));
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> service.page("tenant-a", 42L, "session-1", cursor, 20))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(AiChatBizCodeConstant.INVALID_HISTORY_CURSOR));
    }

    private List<Long> roundIds(ConversationHistoryPageResponse response) {
        return response.getRounds().stream().map(detail -> detail.getRound().getId()).toList();
    }

    private List<Long> windowRoundIds(ConversationHistoryWindowResponse response) {
        return response.getRounds().stream().map(detail -> detail.getRound().getId()).toList();
    }

    private List<ConversationRoundDTO> rounds(long start, long end) {
        return LongStream.rangeClosed(start, end).mapToObj(this::round).toList();
    }

    private ConversationRoundDTO round(long id) {
        ConversationRoundDTO round = new ConversationRoundDTO();
        round.setId(id);
        round.setRoundCode("round-" + id);
        round.setSessionCode("session-1");
        round.setUserId(42L);
        return round;
    }

    private ConversationMessageDTO message(String roundCode, String role, int sortNo, long id) {
        ConversationMessageDTO message = new ConversationMessageDTO();
        message.setId(id);
        message.setRoundCode(roundCode);
        message.setRole(role);
        message.setSortNo(sortNo);
        return message;
    }

    private ConversationArtifactDTO artifact(String roundCode, String artifactType, int seqNo) {
        ConversationArtifactDTO artifact = new ConversationArtifactDTO();
        artifact.setRoundCode(roundCode);
        artifact.setArtifactType(artifactType);
        artifact.setSeqNo(seqNo);
        return artifact;
    }

    private ConversationActivityDTO activity(String roundCode, String name, int seqNo) {
        ConversationActivityDTO activity = new ConversationActivityDTO();
        activity.setRoundCode(roundCode);
        activity.setActivityName(name);
        activity.setSeqNo(seqNo);
        return activity;
    }
}
