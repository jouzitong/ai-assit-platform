package ai.platform.aiassit.execution.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AiModelTestChatRequestDTO {

    private Long id;

    private String providerCode;

    private String baseUrl;

    private String apiModel;

    private String apiKey;

    private List<AiModelTestChatMessageDTO> messages = new ArrayList<>();

    private Map<String, Object> extJson;
}
