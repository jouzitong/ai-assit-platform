package ai.platform.aiassit.conversation.memory;

import ai.platform.aiassit.conversation.memory.config.ConversationMemoryProperties;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBudgetPlannerTest {

    @Test
    void favorsSimilarityAndKeepsSessionAndLongTermBudgetsSeparate() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.getRecall().setTokenBudget(20);
        properties.getRecall().setPerItemTokenLimit(20);
        ContextBudgetPlanner planner = new ContextBudgetPlanner(properties);

        MemoryMessage high = message("高相关", 0.95D, Instant.parse("2026-01-02T00:00:00Z"), MemoryType.SEMANTIC);
        MemoryMessage low = message("低相关", 0.20D, Instant.parse("2026-01-03T00:00:00Z"), MemoryType.EPISODIC);
        MemoryMessage longTerm = message("长期偏好", 0.80D, Instant.parse("2026-01-04T00:00:00Z"), MemoryType.PROCEDURAL);

        ContextBudgetPlanner.BudgetResult result = planner.select(
                List.of(high, low), List.of(longTerm));

        assertThat(result.sessionMessages()).extracting(MemoryMessage::getContent)
                .containsExactly("高相关", "低相关");
        assertThat(result.longTermMessages()).extracting(MemoryMessage::getContent)
                .containsExactly("长期偏好");
    }

    @Test
    void truncatesByCodePointAndNeverSelectsAnItemOverRemainingBudget() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.getRecall().setTokenBudget(10);
        properties.getRecall().setPerItemTokenLimit(4);
        ContextBudgetPlanner planner = new ContextBudgetPlanner(properties);

        MemoryMessage item = message("😀中文记忆", 1D, Instant.now(), MemoryType.SEMANTIC);
        ContextBudgetPlanner.BudgetResult result = planner.select(List.of(item), List.of());

        assertThat(result.sessionMessages()).singleElement()
                .extracting(MemoryMessage::getContent)
                .isEqualTo("😀中文记…");
    }

    private MemoryMessage message(String content, double similarity, Instant createdAt, MemoryType type) {
        MemoryMessage message = new MemoryMessage();
        message.setContent(content);
        message.setSimilarity(similarity);
        message.setCreatedAt(createdAt);
        message.setMemoryType(type);
        return message;
    }
}
