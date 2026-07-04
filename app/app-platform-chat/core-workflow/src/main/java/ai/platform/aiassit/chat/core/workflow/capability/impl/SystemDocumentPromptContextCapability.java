package ai.platform.aiassit.chat.core.workflow.capability.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeCapabilityConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextCapability;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextItem;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于节点配置或请求扩展字段补充系统文档上下文。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Component
public class SystemDocumentPromptContextCapability implements PromptContextCapability {

    public static final String CODE = "system_document_prompt_context";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public PromptContextResult load(WorkflowContext context,
                                    WorkflowNodeConfig nodeConfig,
                                    WorkflowNodeCapabilityConfig capabilityConfig) {
        List<String> documents = resolveDocuments(context.getCommand(), capabilityConfig);
        if (CollectionUtils.isEmpty(documents)) {
            return new PromptContextResult();
        }
        PromptContextItem item = new PromptContextItem();
        item.setTitle(resolveTitle(capabilityConfig, "系统文档上下文"));
        item.setSource(resolveSource(capabilityConfig, "system-document"));
        item.setPriority(resolvePriority(capabilityConfig, 200));
        item.setContent(renderDocuments(documents));
        item.getMetadata().put("documentCount", documents.size());

        PromptContextResult result = new PromptContextResult();
        result.getItems().add(item);
        return result;
    }

    private List<String> resolveDocuments(AiChatQueryCommand command, WorkflowNodeCapabilityConfig capabilityConfig) {
        List<String> documents = new ArrayList<>();
        Map<String, Object> options = capabilityConfig == null ? null : capabilityConfig.getOptions();
        if (options != null) {
            appendDocumentValue(documents, options.get("documents"));
            appendDocumentValue(documents, options.get("content"));
        }
        if (!documents.isEmpty()) {
            return documents;
        }
        Map<String, Object> ext = command == null ? null : command.getExt();
        Object extValue = ext == null ? null : ext.get("systemDocuments");
        appendDocumentValue(documents, extValue);
        extValue = ext == null ? null : ext.get("systemDocument");
        appendDocumentValue(documents, extValue);
        return documents;
    }

    private void appendDocumentValue(List<String> documents, Object value) {
        if (value instanceof String str && StringUtils.hasText(str)) {
            documents.add(str.trim());
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    documents.add(String.valueOf(item).trim());
                }
            }
        }
    }

    private String renderDocuments(List<String> documents) {
        StringBuilder builder = new StringBuilder("以下内容来自系统文档/组件规范，请在回答中优先遵守：");
        for (int i = 0; i < documents.size(); i++) {
            builder.append('\n')
                    .append(i + 1)
                    .append(". ")
                    .append(documents.get(i));
        }
        return builder.toString();
    }

    private String resolveTitle(WorkflowNodeCapabilityConfig capabilityConfig, String fallback) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("title");
        return value instanceof String str && StringUtils.hasText(str) ? str.trim() : fallback;
    }

    private String resolveSource(WorkflowNodeCapabilityConfig capabilityConfig, String fallback) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("source");
        return value instanceof String str && StringUtils.hasText(str) ? str.trim() : fallback;
    }

    private int resolvePriority(WorkflowNodeCapabilityConfig capabilityConfig, int fallback) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("priority");
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
