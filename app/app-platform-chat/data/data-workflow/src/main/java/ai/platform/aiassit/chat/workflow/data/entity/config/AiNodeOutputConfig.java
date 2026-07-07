package ai.platform.aiassit.chat.workflow.data.entity.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 节点输出配置。
 */
@Data
public class AiNodeOutputConfig {

    private String outputType;

    private String storeAs;

    private Map<String, Object> schema = new LinkedHashMap<>();
}
