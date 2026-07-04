package ai.platform.aiassist.service.ai.core.service.impl;

import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchHit;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeRequest;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RerankItem;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.core.service.AiExecutionDomainService;
import ai.platform.aiassist.service.ai.core.service.AiRetrievalDomainService;
import ai.platform.aiassist.service.ai.core.validator.AiRequestValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Service
public class DefaultAiRetrievalDomainService implements AiRetrievalDomainService {

    private static final String INTENT_ANALYZE_PROMPT = """
            你是一个智能问数系统的意图分析器。
            请根据用户问题、历史对话和检索补充上下文，识别用户意图、改写问题，并提取后续检索/SQL生成需要的关键信息。
            你必须只返回严格合法的 JSON，不允许输出 markdown、解释、代码块。
            输出结构固定如下：
            {
              "intentType": "意图类型，仅允许 SIMPLE_CHAT 或 QUERY_RENDER",
              "rewrittenQuery": "归一化后的问题",
              "summary": "简明分析摘要",
              "intentLabels": ["标签1"],
              "metrics": ["指标1"],
              "dimensions": ["维度1"],
              "candidateDatasets": ["数据集1"],
              "requiredContext": ["需要补充的上下文1"],
              "risks": ["风险1"],
              "clarificationNeeded": false,
              "clarificationQuestions": ["澄清问题1"],
              "timeRange": {"granularity":"DAY","startDate":"2026-06-01","endDate":"2026-06-15"}
            }
            字段要求：
            1. intentType、rewrittenQuery、summary 必须为非空字符串
            2. 所有数组字段必须返回 JSON 数组，可为空数组
            3. clarificationNeeded 必须为布尔值
            4. timeRange 必须返回 JSON 对象，可为空对象
            5. intentType 取值规则：
               - SIMPLE_CHAT：普通闲聊、解释、问答、建议，不需要进入查数渲染链路
               - QUERY_RENDER：用户核心目标是查数据、分析数据、生成报表、做指标解释或需要进入查询渲染链路
            6. 如果无法绝对确认，但问题明显偏向数据查询/分析，优先返回 QUERY_RENDER
            7. 不要输出任何额外字段
            """;

    private final AiExecutionDomainService aiExecutionDomainService;
    private final AiRequestValidator aiRequestValidator;
    private final ObjectMapper objectMapper;

    public DefaultAiRetrievalDomainService(AiExecutionDomainService aiExecutionDomainService,
                                           AiRequestValidator aiRequestValidator,
                                           ObjectMapper objectMapper) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.aiRequestValidator = aiRequestValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public HybridSearchResponse hybridSearch(HybridSearchRequest request) {
        aiRequestValidator.validateHybridSearch(request);
        HybridSearchResponse response = new HybridSearchResponse();
        response.setKbId(request.getKbId());
        response.setQuery(request.getQuery());
        response.setRetrievalMode(resolveRetrievalMode(request));

        KbSearchRequest kbSearchRequest = new KbSearchRequest();
        kbSearchRequest.setKbId(request.getKbId());
        kbSearchRequest.setQuery(request.getQuery());
        kbSearchRequest.setTopK(resolveTopK(request));
        kbSearchRequest.setMeta(copyMeta(request.getMeta()));
        KbSearchResponse kbSearchResponse = aiExecutionDomainService.kbSearch(kbSearchRequest);
        response.setHits(toHybridHits(kbSearchResponse));

        if (Boolean.TRUE.equals(request.getRerankEnabled()) && !CollectionUtils.isEmpty(response.getHits())) {
            try {
                rerankHits(request, response);
            } catch (Exception ex) {
                response.setDegraded(Boolean.TRUE);
                response.setDegradedReason("rerank degraded: " + ex.getMessage());
            }
        }
        return response;
    }

    @Override
    public IntentAnalyzeResponse analyzeIntent(IntentAnalyzeRequest request) {
        aiRequestValidator.validateIntentAnalyze(request);
        ChatRequest chatRequest = buildIntentAnalyzeChatRequest(request);
        ChatResponse chatResponse = aiExecutionDomainService.chat(chatRequest);
        String rawOutput = extractAnswer(chatResponse);

        IntentAnalyzeResponse response;
        try {
            response = objectMapper.readValue(cleanJson(rawOutput), IntentAnalyzeResponse.class);
        } catch (Exception ex) {
            response = fallbackIntentAnalyzeResponse(request, rawOutput);
        }
        response.setRawOutput(rawOutput);
        response.setRequestId(chatResponse == null ? null : chatResponse.getRequestId());
        response.setModel(chatResponse == null ? request.getModel() : chatResponse.getModel());
        normalizeIntentResponse(response, request);
        return response;
    }

    private ChatRequest buildIntentAnalyzeChatRequest(IntentAnalyzeRequest request) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setProvider(request.getProvider());
        chatRequest.setModel(request.getModel());
        chatRequest.setMeta(copyMeta(request.getMeta()));
        chatRequest.getMeta().setScene(StringUtils.hasText(request.getScene())
                ? request.getScene() : "ai-retrieval-intent-analyze");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, INTENT_ANALYZE_PROMPT));
        String context = buildIntentContext(request);
        if (StringUtils.hasText(context)) {
            messages.add(buildMessage(MessageRole.SYSTEM, context));
        }
        if (!CollectionUtils.isEmpty(request.getHistory())) {
            messages.addAll(request.getHistory());
        }
        messages.add(buildMessage(MessageRole.USER, request.getQuery()));
        chatRequest.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(1024);
        options.setTimeoutMs(30_000);
        chatRequest.setOptions(options);
        chatRequest.setExt(request.getExt());
        return chatRequest;
    }

    private String buildIntentContext(IntentAnalyzeRequest request) {
        StringJoiner joiner = new StringJoiner("\n");
        if (StringUtils.hasText(request.getScene())) {
            joiner.add("业务场景：" + request.getScene());
        }
        if (StringUtils.hasText(request.getRetrievalContext())) {
            joiner.add("检索补充上下文：" + request.getRetrievalContext());
        }
        return joiner.toString();
    }

    private IntentAnalyzeResponse fallbackIntentAnalyzeResponse(IntentAnalyzeRequest request, String rawOutput) {
        IntentAnalyzeResponse response = new IntentAnalyzeResponse();
        response.setIntentType("QUERY_RENDER");
        response.setRewrittenQuery(request.getQuery());
        response.setSummary(StringUtils.hasText(rawOutput) ? rawOutput.trim() : request.getQuery());
        return response;
    }

    private void normalizeIntentResponse(IntentAnalyzeResponse response, IntentAnalyzeRequest request) {
        if (!StringUtils.hasText(response.getIntentType())) {
            response.setIntentType("QUERY_RENDER");
        }
        String normalizedIntentType = response.getIntentType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"SIMPLE_CHAT".equals(normalizedIntentType) && !"QUERY_RENDER".equals(normalizedIntentType)) {
            normalizedIntentType = "QUERY_RENDER";
        }
        response.setIntentType(normalizedIntentType);
        if (!StringUtils.hasText(response.getRewrittenQuery())) {
            response.setRewrittenQuery(request.getQuery());
        }
        if (!StringUtils.hasText(response.getSummary())) {
            response.setSummary(response.getRewrittenQuery());
        }
        if (response.getIntentLabels() == null) {
            response.setIntentLabels(new ArrayList<>());
        }
        if (response.getMetrics() == null) {
            response.setMetrics(new ArrayList<>());
        }
        if (response.getDimensions() == null) {
            response.setDimensions(new ArrayList<>());
        }
        if (response.getCandidateDatasets() == null) {
            response.setCandidateDatasets(new ArrayList<>());
        }
        if (response.getRequiredContext() == null) {
            response.setRequiredContext(new ArrayList<>());
        }
        if (response.getRisks() == null) {
            response.setRisks(new ArrayList<>());
        }
        if (response.getClarificationQuestions() == null) {
            response.setClarificationQuestions(new ArrayList<>());
        }
        if (response.getImportantInfos() == null) {
            response.setImportantInfos(new ArrayList<>());
        }
        if (response.getTimeRange() == null) {
            response.setTimeRange(new java.util.HashMap<>());
        }
        if (response.getClarificationNeeded() == null) {
            response.setClarificationNeeded(Boolean.FALSE);
        }
    }

    private void rerankHits(HybridSearchRequest request, HybridSearchResponse response) {
        RerankRequest rerankRequest = new RerankRequest();
        rerankRequest.setProvider(request.getProvider());
        rerankRequest.setQuery(request.getQuery());
        rerankRequest.setTopN(resolveTopK(request));
        rerankRequest.setMeta(copyMeta(request.getMeta()));
        rerankRequest.setCandidates(response.getHits().stream().map(HybridSearchHit::getContent).toList());
        RerankResponse rerankResponse = aiExecutionDomainService.rerank(rerankRequest);
        if (rerankResponse == null || CollectionUtils.isEmpty(rerankResponse.getItems())) {
            return;
        }
        List<HybridSearchHit> rerankedHits = new ArrayList<>();
        for (RerankItem item : rerankResponse.getItems()) {
            if (item == null || item.getIndex() == null || item.getIndex() < 0 || item.getIndex() >= response.getHits().size()) {
                continue;
            }
            HybridSearchHit hit = response.getHits().get(item.getIndex());
            hit.setRerankScore(item.getScore());
            hit.setFinalScore(item.getScore());
            rerankedHits.add(hit);
        }
        if (!rerankedHits.isEmpty()) {
            rerankedHits.sort(Comparator.comparing(HybridSearchHit::getFinalScore, Comparator.nullsLast(Double::compareTo)).reversed());
            response.setHits(rerankedHits);
            response.setReranked(Boolean.TRUE);
        }
    }

    private List<HybridSearchHit> toHybridHits(KbSearchResponse kbSearchResponse) {
        List<HybridSearchHit> hits = new ArrayList<>();
        if (kbSearchResponse == null || CollectionUtils.isEmpty(kbSearchResponse.getItems())) {
            return hits;
        }
        for (KbSearchItem item : kbSearchResponse.getItems()) {
            if (item == null) {
                continue;
            }
            HybridSearchHit hit = new HybridSearchHit();
            hit.setDocumentId(item.getDocumentId());
            hit.setContent(item.getContent());
            hit.setMetadata(item.getMetadata());
            hit.setSourceType("KB");
            hit.setScore(item.getScore());
            hit.setFinalScore(item.getScore());
            hits.add(hit);
        }
        return hits;
    }

    private String resolveRetrievalMode(HybridSearchRequest request) {
        boolean keywordEnabled = !Boolean.FALSE.equals(request.getKeywordEnabled());
        boolean vectorEnabled = !Boolean.FALSE.equals(request.getVectorEnabled());
        if (keywordEnabled && vectorEnabled) {
            return "HYBRID";
        }
        if (keywordEnabled) {
            return "KEYWORD";
        }
        if (vectorEnabled) {
            return "VECTOR";
        }
        return "DEFAULT";
    }

    private Integer resolveTopK(HybridSearchRequest request) {
        if (request.getTopK() != null && request.getTopK() > 0) {
            return request.getTopK();
        }
        return 5;
    }

    private RequestMeta copyMeta(RequestMeta source) {
        RequestMeta target = new RequestMeta();
        if (source == null) {
            return target;
        }
        target.setTraceId(source.getTraceId());
        target.setScene(source.getScene());
        target.setTenantId(source.getTenantId());
        if (source.getExt() != null && !source.getExt().isEmpty()) {
            target.setExt(new java.util.HashMap<>(source.getExt()));
        }
        return target;
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return "";
        }
        return response.getOutputs().stream()
                .filter(Objects::nonNull)
                .map(OutputItem::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String cleanJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
    }
}
