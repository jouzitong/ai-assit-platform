package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.protocol.RenderArtifactResponse;
import ai.platform.aiassit.conversation.dto.protocol.RoundThinkingResponse;

public interface ConversationProtocolQueryService {

    RoundThinkingResponse thinkingDetail(String sessionCode, String roundCode, Long userId);

    RenderArtifactResponse renderArtifact(String codeRef, Long userId);
}
