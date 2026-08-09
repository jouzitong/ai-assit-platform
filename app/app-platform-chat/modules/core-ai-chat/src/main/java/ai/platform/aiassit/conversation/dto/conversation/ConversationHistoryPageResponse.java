package ai.platform.aiassit.conversation.dto.conversation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A bounded, cursor-based page of persisted conversation rounds. */
@Data
public class ConversationHistoryPageResponse {

    private String sessionCode;

    private List<ConversationRoundDetailVO> rounds = new ArrayList<>();

    /** Opaque cursor for loading rounds older than the current page. */
    private String nextCursor;

    private boolean hasMore;
}
