package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative fallback token planner used without persisting a context snapshot. */
@Component
public class ContextBudgetPlanner {

    private final ConversationMemoryProperties properties;

    public ContextBudgetPlanner(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    public BudgetResult select(List<MemoryMessage> sessionCandidates, List<MemoryMessage> longTermCandidates) {
        int totalBudget = Math.max(0, properties.getRecall().getTokenBudget());
        int sessionBudget = (int) Math.floor(totalBudget * 0.72D);
        int longTermBudget = totalBudget - sessionBudget;
        return new BudgetResult(
                selectWithin(sessionCandidates, sessionBudget),
                selectWithin(longTermCandidates, longTermBudget));
    }

    private List<MemoryMessage> selectWithin(List<MemoryMessage> candidates, int budget) {
        if (budget <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<MemoryMessage> ordered = candidates.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getContent()))
                .sorted(Comparator
                        .comparing((MemoryMessage item) -> item.getSimilarity() == null ? 0D : item.getSimilarity())
                        .reversed()
                        .thenComparing(item -> item.getCreatedAt() == null
                                ? java.time.Instant.EPOCH : item.getCreatedAt(), Comparator.reverseOrder()))
                .toList();
        int remaining = budget;
        List<MemoryMessage> selected = new ArrayList<>();
        for (MemoryMessage item : ordered) {
            String content = truncateToTokens(item.getContent(), properties.getRecall().getPerItemTokenLimit());
            int tokens = estimateTokens(content);
            if (tokens <= 0 || tokens > remaining) {
                continue;
            }
            MemoryMessage copy = copy(item, content);
            selected.add(copy);
            remaining -= tokens;
        }
        return selected;
    }

    int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        // One Unicode code point per token is intentionally conservative for Chinese-heavy content.
        return content.codePointCount(0, content.length());
    }

    private String truncateToTokens(String content, int maxTokens) {
        if (!StringUtils.hasText(content) || maxTokens <= 0) {
            return "";
        }
        String normalized = content.trim();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maxTokens) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, maxTokens);
        return normalized.substring(0, end) + "…";
    }

    private MemoryMessage copy(MemoryMessage source, String content) {
        MemoryMessage result = new MemoryMessage();
        result.setMemoryId(source.getMemoryId());
        result.setMessageId(source.getMessageId());
        result.setMemoryType(source.getMemoryType());
        result.setContent(content);
        result.setSimilarity(source.getSimilarity());
        result.setEnabled(source.getEnabled());
        result.setAgentId(source.getAgentId());
        result.setSessionId(source.getSessionId());
        result.setUserId(source.getUserId());
        result.setSourceId(source.getSourceId());
        result.setProcessingStatus(source.getProcessingStatus());
        result.setCreatedAt(source.getCreatedAt());
        return result;
    }

    public record BudgetResult(List<MemoryMessage> sessionMessages, List<MemoryMessage> longTermMessages) {
    }
}
