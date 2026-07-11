package ai.platform.aiassit.conversation.protocol.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ChatTransportRequest {

    private String type;

    private String requestId;

    private String runId;

    private String lastEventId;

    private String sessionCode;

    private String roundCode;

    private String modelCode;

    private Message message;

    private Map<String, Object> clientContext = new LinkedHashMap<>();

    @Data
    public static class Message {

        private String id;

        private String role;

        private String createdAt;

        private List<Content> content = new ArrayList<>();

        public String text() {
            return content == null ? "" : content.stream()
                    .filter(item -> item != null && "text".equalsIgnoreCase(item.getType()))
                    .map(Content::getText)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
        }
    }

    @Data
    public static class Content {

        private String type;

        private String text;
    }
}
