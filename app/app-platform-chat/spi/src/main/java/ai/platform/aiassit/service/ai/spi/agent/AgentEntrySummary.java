package ai.platform.aiassit.service.ai.spi.agent;

import lombok.Builder;
import lombok.Value;

/** Safe public projection for an Agent available at a product entry. */
@Value
@Builder
public class AgentEntrySummary {
    String code;
    String name;
    String description;
    Integer version;
}
