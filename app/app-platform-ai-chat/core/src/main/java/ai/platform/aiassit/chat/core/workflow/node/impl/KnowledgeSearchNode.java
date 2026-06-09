package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiProviderModelOverviewDTO;
import ai.platform.aiassist.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识检索节点，负责补充 SQL 生成所需的口径与背景信息。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class KnowledgeSearchNode extends BaseWorkflowNode {

    private final AiKnowledgeBaseExecutionApi knowledgeBaseExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final WorkflowHistoryRecorder historyRecorder;

    public KnowledgeSearchNode(AiKnowledgeBaseExecutionApi knowledgeBaseExecutionApi,
                               AiMetaQueryApi aiMetaQueryApi,
                               WorkflowHistoryRecorder historyRecorder) {
        this.knowledgeBaseExecutionApi = knowledgeBaseExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }

        String kbId = resolveKnowledgeBaseId(command);
        context.setKnowledgeBaseId(kbId);

        List<String> knowledgeParts = new ArrayList<>();
        if (StringUtils.hasText(kbId)) {
            try {
                KbSearchResponse response = searchKnowledgeBase(command, context, kbId);
                context.put("knowledgeSearchResponse", response);
                if (response != null && !CollectionUtils.isEmpty(response.getItems())) {
                    knowledgeParts.add(formatKnowledgeHits(response.getItems()));
                }
            } catch (Exception ex) {
                log.warn("knowledge search degraded, kbId={}, sessionCode={}", kbId,
                        context.getSession() == null ? null : context.getSession().getSessionCode(), ex);
                context.put("knowledgeSearchError", ex.getMessage());
            }
        }

        knowledgeParts.add(buildModelOverviewSummary());

        String knowledgeResult = knowledgeParts.stream()
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("未检索到可用知识上下文。");

        context.setKnowledgeResult(knowledgeResult);
        context.put("knowledgeResult", knowledgeResult);
        historyRecorder.saveArtifact(
                context,
                AiChatArtifactType.KNOWLEDGE_RESULT.name(),
                AiChatArtifactStage.KNOWLEDGE.name(),
                "知识检索结果",
                knowledgeResult,
                AiChatContentFormat.MARKDOWN.name(),
                true,
                "SUCCESS",
                context.getCurrentUserMessage() == null ? null : context.getCurrentUserMessage().getMessageCode(),
                context.get("knowledgeSearchResponse")
        );
        return NodeResult.success(null);
    }

    @Override
    public String type() {
        return "Knowledge-Search";
    }

    @Override
    public int order() {
        return 300;
    }

    private KbSearchResponse searchKnowledgeBase(AiChatQueryCommand command, WorkflowContext context, String kbId) {
        KbSearchRequest request = new KbSearchRequest();
        request.setKbId(kbId);
        request.setQuery(buildKnowledgeQuery(command, context));
        request.setTopK(resolveTopK(command));

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : "ai-chat-knowledge-search");
        request.setMeta(meta);
        return knowledgeBaseExecutionApi.kbSearch(request);
    }

    private String buildKnowledgeQuery(AiChatQueryCommand command, WorkflowContext context) {
        if (StringUtils.hasText(context.getAnalysisResult())) {
            return context.getAnalysisResult();
        }
        return command.getMessage();
    }

    private int resolveTopK(AiChatQueryCommand command) {
        Object value = safeGet(command.getExt(), "kbTopK");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 5;
    }

    private String resolveKnowledgeBaseId(AiChatQueryCommand command) {
        Object extValue = safeGet(command.getExt(), "kbId");
        if (extValue == null) {
            extValue = safeGet(command.getExt(), "knowledgeBaseId");
        }
        if (extValue instanceof String str && StringUtils.hasText(str)) {
            return str.trim();
        }
        if (!CollectionUtils.isEmpty(command.getTools())) {
            for (AiChatToolDTO tool : command.getTools()) {
                if (tool == null || tool.getExt() == null) {
                    continue;
                }
                if (matchesKnowledgeTool(tool.getToolCode()) || matchesKnowledgeTool(tool.getToolName())) {
                    Object kbId = safeGet(tool.getExt(), "kbId");
                    if (kbId == null) {
                        kbId = safeGet(tool.getExt(), "knowledgeBaseId");
                    }
                    if (kbId instanceof String str && StringUtils.hasText(str)) {
                        return str.trim();
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesKnowledgeTool(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("knowledge") || normalized.contains("kb");
    }

    private String formatKnowledgeHits(List<KbSearchItem> items) {
        StringBuilder builder = new StringBuilder("知识库命中摘要：");
        int index = 1;
        for (KbSearchItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            builder.append('\n')
                    .append(index++)
                    .append(". ")
                    .append(item.getContent().trim());
            if (!CollectionUtils.isEmpty(item.getMetadata())) {
                builder.append(" | metadata=")
                        .append(item.getMetadata());
            }
        }
        return builder.toString();
    }

    private String buildModelOverviewSummary() {
        try {
            AiMetaQueryRequest request = new AiMetaQueryRequest();
            request.setEnabled(Boolean.TRUE);
            AiProviderModelOverviewDTO overview = aiMetaQueryApi.providerModelOverview(request);
            if (overview == null || CollectionUtils.isEmpty(overview.getProviders())) {
                return "";
            }
            StringBuilder builder = new StringBuilder("当前可用模型概览：");
            for (AiProviderModelOverviewDTO.ProviderItem provider : overview.getProviders()) {
                if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                    continue;
                }
                builder.append('\n')
                        .append("- provider=")
                        .append(provider.getProviderCode())
                        .append(", models=");
                List<String> models = new ArrayList<>();
                if (!CollectionUtils.isEmpty(provider.getModels())) {
                    for (AiProviderModelOverviewDTO.ModelItem model : provider.getModels()) {
                        if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
                            continue;
                        }
                        models.add(model.getApiModel());
                    }
                }
                builder.append(models);
            }
            return builder.toString();
        } catch (Exception ex) {
            log.warn("load model overview failed", ex);
            return "";
        }
    }

    private Object safeGet(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }
}
