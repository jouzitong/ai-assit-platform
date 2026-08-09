package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A bounded round window used when a context item links back to its source. */
@Data
public class ConversationHistoryWindowResponse {

    private String sessionCode;

    private String aroundRoundCode;

    private List<ConversationRoundDetailVO> rounds = new ArrayList<>();

    private String beforeCursor;

    private String afterCursor;

    private boolean hasEarlier;

    private boolean hasLater;
}
