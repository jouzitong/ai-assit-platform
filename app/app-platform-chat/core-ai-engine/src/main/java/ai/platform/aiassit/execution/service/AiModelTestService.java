package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.execution.dto.AiModelTestChatRequestDTO;
import ai.platform.aiassit.execution.dto.AiModelTestChatResultVO;

public interface AiModelTestService {

    AiModelTestChatResultVO testChat(AiModelTestChatRequestDTO request);
}
