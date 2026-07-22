package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConversationHistoryRecorderTest {

    @Test
    void updatesOneRecordForTheSameActivityLifecycle() {
        List<ConversationActivityDTO> records = new ArrayList<>();
        AtomicInteger addCount = new AtomicInteger();
        AtomicInteger updateCount = new AtomicInteger();
        ConversationActivityService activityService = proxy(ConversationActivityService.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "queryAll" -> List.copyOf(records);
                case "add" -> {
                    ConversationActivityDTO record = (ConversationActivityDTO) args[0];
                    record.setId(101L);
                    records.add(record);
                    addCount.incrementAndGet();
                    yield record;
                }
                case "update" -> {
                    ConversationActivityDTO record = (ConversationActivityDTO) args[1];
                    records.clear();
                    records.add(record);
                    updateCount.incrementAndGet();
                    yield record;
                }
                default -> null;
            };
        });
        AgentConversationHistoryRecorder recorder = new AgentConversationHistoryRecorder(
                proxy(ConversationMessageService.class, (proxy, method, args) -> null),
                proxy(ConversationArtifactService.class, (proxy, method, args) -> null),
                activityService,
                new ObjectMapper()
        );
        ConversationRuntimeContext context = context();
        Instant startedAt = Instant.parse("2026-07-21T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-07-21T08:00:02Z");

        ConversationActivityDTO started = recorder.saveActivity(
                context,
                "AI_AGENT",
                "STARTED",
                "开始调用工具",
                "RUNNING",
                Map.of(
                        "activityCode", "call-1",
                        "activityType", "TOOL_CALL",
                        "activityName", "调用工具：数据库查询",
                        "inputSummary", "查询当前数据库中的表",
                        "timestamp", startedAt
                )
        ).orElseThrow();

        ConversationActivityDTO completed = recorder.saveActivity(
                context,
                "AI_AGENT",
                "COMPLETED",
                "工具调用完成",
                "SUCCESS",
                Map.of(
                        "activityCode", "call-1",
                        "activityType", "TOOL_CALL",
                        "outputSummary", "查询到 3 张业务表",
                        "timestamp", finishedAt
                )
        ).orElseThrow();

        assertThat(completed.getId()).isEqualTo(101L);
        assertThat(completed.getSeqNo()).isEqualTo(1);
        assertThat(completed.getCorrelationCode()).isEqualTo("call-1");
        assertThat(completed.getActivityName()).isEqualTo("调用工具：数据库查询");
        assertThat(completed.getInputSummary()).isEqualTo("查询当前数据库中的表");
        assertThat(completed.getOutputSummary()).isEqualTo("查询到 3 张业务表");
        assertThat(completed.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getStartedAt()).isEqualTo(startedAt);
        assertThat(completed.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(completed.getDurationMs()).isEqualTo(2_000L);
        assertThat(addCount).hasValue(1);
        assertThat(updateCount).hasValue(1);
    }

    @Test
    void partialAndInputRequiredStatusesCloseActivityTiming() {
        assertTerminalStatusClosesTiming("PARTIAL");
        assertTerminalStatusClosesTiming("INPUT_REQUIRED");
    }

    private void assertTerminalStatusClosesTiming(String terminalStatus) {
        List<ConversationActivityDTO> records = new ArrayList<>();
        ConversationActivityService activityService = proxy(ConversationActivityService.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "queryAll" -> List.copyOf(records);
                    case "add" -> {
                        ConversationActivityDTO record = (ConversationActivityDTO) args[0];
                        record.setId(202L);
                        records.add(record);
                        yield record;
                    }
                    case "update" -> {
                        ConversationActivityDTO record = (ConversationActivityDTO) args[1];
                        records.set(0, record);
                        yield record;
                    }
                    default -> null;
                });
        AgentConversationHistoryRecorder recorder = new AgentConversationHistoryRecorder(
                proxy(ConversationMessageService.class, (proxy, method, args) -> null),
                proxy(ConversationArtifactService.class, (proxy, method, args) -> null),
                activityService,
                new ObjectMapper()
        );
        Instant startedAt = Instant.parse("2026-07-21T09:00:00Z");
        Instant finishedAt = Instant.parse("2026-07-21T09:00:03Z");

        recorder.saveActivity(context(), "AI_AGENT", "STARTED", "执行开始", "RUNNING", Map.of(
                "activityCode", "execution-result",
                "timestamp", startedAt
        )).orElseThrow();
        ConversationActivityDTO completed = recorder.saveActivity(
                context(), "AI_AGENT", "COMPLETED", "执行完成", terminalStatus, Map.of(
                        "activityCode", "execution-result",
                        "timestamp", finishedAt
                )
        ).orElseThrow();

        assertThat(completed.getStatus()).isEqualTo(terminalStatus);
        assertThat(completed.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(completed.getDurationMs()).isEqualTo(3_000L);
    }

    private ConversationRuntimeContext context() {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode("session-1");
        session.setUserId(7L);
        ConversationRoundDTO round = new ConversationRoundDTO();
        round.setRoundCode("round-1");
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setTraceId("trace-1");

        ConversationRuntimeContext context = new ConversationRuntimeContext();
        context.setSession(session);
        context.setRound(round);
        context.setCommand(command);
        return context;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
