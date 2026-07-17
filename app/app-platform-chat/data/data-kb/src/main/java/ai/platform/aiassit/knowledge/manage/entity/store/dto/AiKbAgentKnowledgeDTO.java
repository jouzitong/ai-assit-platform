package ai.platform.aiassit.knowledge.manage.entity.store.dto;

import lombok.Data;

import java.util.List;

/** Secret-free knowledge-base metadata exposed to an Agent run. */
@Data
public class AiKbAgentKnowledgeDTO {

    /** Stable local KB code accepted by the retrieval API. */
    private String kbCode;

    /** Human-readable KB name used by an Agent to choose a source. */
    private String name;

    /** Short business description. */
    private String description;

    /** Optional search tags. */
    private List<String> tags;
}
