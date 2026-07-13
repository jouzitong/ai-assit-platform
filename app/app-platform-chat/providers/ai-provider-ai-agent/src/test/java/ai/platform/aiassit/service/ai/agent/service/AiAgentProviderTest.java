package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiAgentProviderTest {

    @Test
    void emitsFallbackFailureActivityWhenProcessFailsBeforePythonErrorFrame() {
        AiAgentProperties properties = new AiAgentProperties();
        AiAgentProcessExecutor processExecutor = mock(AiAgentProcessExecutor.class);
        ProviderChatRequest request = new ProviderChatRequest();
        ChatStreamObserver observer = mock(ChatStreamObserver.class);
        IllegalStateException failure = new IllegalStateException("python process timeout");
        doThrow(failure).when(processExecutor).executeStream(eq(properties), eq(request), any());

        new AiAgentProvider(properties, processExecutor, new ObjectMapper()).chatStream(request, observer);

        ArgumentCaptor<ChatChunk> chunk = ArgumentCaptor.forClass(ChatChunk.class);
        verify(observer).onChunk(chunk.capture());
        assertThat(chunk.getValue().getProgressType()).isEqualTo("ACTIVITY");
        assertThat(chunk.getValue().getPhase()).isEqualTo("FAILED");
        assertThat(chunk.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(chunk.getValue().getMessage()).isEqualTo("AI Agent 执行超时");
        assertThat(chunk.getValue().getExt()).containsEntry("activityType", "AI_AGENT_EXECUTION");
        verify(observer).onError(failure);
    }
}
