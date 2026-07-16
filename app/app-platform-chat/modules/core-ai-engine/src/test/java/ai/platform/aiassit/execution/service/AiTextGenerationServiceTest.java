package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTextGenerationServiceTest {

    @Test
    void executesSimpleTaskOnlyThroughSpringAiClient() {
        AiModelConfigDTO config = model(AiChatClientType.SPRING_AI);
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        AiTextGenerationService service = new AiTextGenerationService(
                execution(captured), modelService(config));
        AiTextGenerationRequest request = request();
        request.setMaxTokens(10);
        request.setTemperature(4D);

        var response = service.generate(request);

        assertThat(response.getText()).isEqualTo("generated answer");
        assertThat(captured.get().getClientType()).isEqualTo(AiChatClientType.SPRING_AI);
        assertThat(captured.get().getOptions().getMaxTokens()).isEqualTo(64);
        assertThat(captured.get().getOptions().getTemperature()).isEqualTo(1D);
    }

    @Test
    void rejectsAgentModelFromSimpleTaskEntry() {
        AiTextGenerationService service = new AiTextGenerationService(
                execution(new AtomicReference<>()), modelService(model(AiChatClientType.AI_AGENT)));

        assertThatThrownBy(() -> service.generate(request()))
                .isInstanceOf(BizException.class);
    }

    private AiExecutionDomainService execution(AtomicReference<ChatRequest> captured) {
        return new AiExecutionDomainService() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                captured.set(request);
                OutputItem output = new OutputItem();
                output.setType(OutputType.TEXT);
                output.setText("generated answer");
                ChatResponse response = new ChatResponse();
                response.setRequestId("request-1");
                response.setModel("gpt-test");
                response.getOutputs().add(output);
                return response;
            }

            @Override
            public void chatStream(ChatRequest request, ChatStreamObserver observer) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void chatStreamAsync(ChatRequest request, ChatStreamObserver observer) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AiModelConfigService modelService(AiModelConfigDTO config) {
        return (AiModelConfigService) Proxy.newProxyInstance(
                AiModelConfigService.class.getClassLoader(),
                new Class<?>[]{AiModelConfigService.class},
                (proxy, method, args) -> {
                    if ("getByModelCode".equals(method.getName())) {
                        return config;
                    }
                    if ("toString".equals(method.getName())) {
                        return "AiModelConfigServiceStub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }

    private AiModelConfigDTO model(AiChatClientType clientType) {
        AiModelConfigDTO config = new AiModelConfigDTO();
        config.setModelCode("simple-model");
        config.setClientType(clientType);
        config.setEnabled(true);
        return config;
    }

    private AiTextGenerationRequest request() {
        AiTextGenerationRequest request = new AiTextGenerationRequest();
        request.setModelCode("simple-model");
        request.setSystemPrompt("Be concise");
        request.setUserPrompt("Summarize this");
        return request;
    }
}
