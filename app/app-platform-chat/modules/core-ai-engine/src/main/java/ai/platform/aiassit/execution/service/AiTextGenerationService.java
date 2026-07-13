package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 为内部管理场景提供不创建会话记录的单次文本生成。 */
@Service
public class AiTextGenerationService {

    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int MIN_MAX_TOKENS = 64;
    private static final int MAX_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.2D;

    private final AiExecutionDomainService executionDomainService;

    public AiTextGenerationService(AiExecutionDomainService executionDomainService) {
        this.executionDomainService = executionDomainService;
    }

    public AiTextGenerationResponse generate(AiTextGenerationRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserPrompt())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
        }

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setClientType(AiChatClientType.SPRING_AI);
        chatRequest.setMessages(messages(request));
        chatRequest.getOptions().setTemperature(resolveTemperature(request.getTemperature()));
        chatRequest.getOptions().setMaxTokens(resolveMaxTokens(request.getMaxTokens()));
        chatRequest.getOptions().setTimeoutMs(60_000);
        chatRequest.getMeta().setScene(StringUtils.hasText(request.getScene()) ? request.getScene().trim() : "internal-text-generation");

        ChatResponse response = executionDomainService.chat(chatRequest);
        String text = extractText(response);
        if (!StringUtils.hasText(text)) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, "AI 未返回文本内容");
        }
        return new AiTextGenerationResponse(text.trim(), response.getModel(), response.getRequestId());
    }

    private List<ChatMessage> messages(AiTextGenerationRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(message(MessageRole.SYSTEM, request.getSystemPrompt().trim()));
        }
        messages.add(message(MessageRole.USER, request.getUserPrompt().trim()));
        return messages;
    }

    private ChatMessage message(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private int resolveMaxTokens(Integer value) {
        int resolved = value == null ? DEFAULT_MAX_TOKENS : value;
        return Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, resolved));
    }

    private double resolveTemperature(Double value) {
        double resolved = value == null ? DEFAULT_TEMPERATURE : value;
        return Math.max(0D, Math.min(1D, resolved));
    }

    private String extractText(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return null;
        }
        return response.getOutputs().stream()
                .filter(item -> item != null && StringUtils.hasText(item.getText()))
                .map(OutputItem::getText)
                .findFirst()
                .orElse(null);
    }
}
