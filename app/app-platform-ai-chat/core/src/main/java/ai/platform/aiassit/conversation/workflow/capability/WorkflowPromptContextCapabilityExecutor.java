package ai.platform.aiassit.conversation.workflow.capability;

import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeCapabilityConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt 上下文 Capability 执行器。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Component
public class WorkflowPromptContextCapabilityExecutor {

    private static final String OUTPUT_KEY_PROMPT_CONTEXT = "promptContext";
    private static final String OUTPUT_KEY_PROMPT_CONTEXT_ITEMS = "promptContextItems";
    private static final String OUTPUT_KEY_PROMPT_CONTEXT_SOURCES = "promptContextSources";

    private final Map<String, PromptContextCapability> capabilityRegistry = new HashMap<>();

    public WorkflowPromptContextCapabilityExecutor(List<PromptContextCapability> capabilities) {
        for (PromptContextCapability capability : capabilities) {
            capabilityRegistry.put(capability.code(), capability);
        }
    }

    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig) {
        if (nodeConfig == null || CollectionUtils.isEmpty(nodeConfig.getCapabilities())) {
            return NodeResult.success(null);
        }
        List<WorkflowNodeCapabilityConfig> capabilityConfigs = nodeConfig.getCapabilities().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(WorkflowNodeCapabilityConfig::getSort, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<PromptContextItem> items = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (WorkflowNodeCapabilityConfig capabilityConfig : capabilityConfigs) {
            if (!StringUtils.hasText(capabilityConfig.getCode())) {
                continue;
            }
            PromptContextCapability capability = capabilityRegistry.get(capabilityConfig.getCode());
            if (capability == null) {
                if (Boolean.TRUE.equals(capabilityConfig.getRequired())) {
                    return NodeResult.fail("workflow capability not found: " + capabilityConfig.getCode());
                }
                continue;
            }
            PromptContextResult result = capability.load(context, nodeConfig, capabilityConfig);
            if (result == null || CollectionUtils.isEmpty(result.getItems())) {
                continue;
            }
            items.addAll(result.getItems());
            sources.add(capability.code());
        }
        items.sort(Comparator.comparing(PromptContextItem::getPriority, Comparator.nullsLast(Integer::compareTo)));
        String promptContext = renderPromptContext(items);
        context.setPromptContext(nodeConfig.getNodeId(), promptContext);
        context.putNodeOutput(nodeConfig.getNodeId(), OUTPUT_KEY_PROMPT_CONTEXT_ITEMS, items);
        context.putNodeOutput(nodeConfig.getNodeId(), OUTPUT_KEY_PROMPT_CONTEXT_SOURCES, sources);
        return NodeResult.success(null);
    }

    private String renderPromptContext(List<PromptContextItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (PromptContextItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            if (StringUtils.hasText(item.getTitle())) {
                builder.append("[").append(item.getTitle().trim()).append("]\n");
            }
            builder.append(item.getContent().trim());
            if (StringUtils.hasText(item.getSource())) {
                builder.append("\nsource=").append(item.getSource().trim());
            }
        }
        return builder.toString().trim();
    }
}
