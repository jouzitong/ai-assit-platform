package ai.platform.aiassit.execution.dto;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AiModelTestChatRequestDTO {

    private Long id;

    private AiChatClientType clientType;

    private String baseUrl;

    private String apiModel;

    private String apiKey;

    private List<AiModelTestChatMessageDTO> messages = new ArrayList<>();

    private Map<String, Object> extJson;
}
