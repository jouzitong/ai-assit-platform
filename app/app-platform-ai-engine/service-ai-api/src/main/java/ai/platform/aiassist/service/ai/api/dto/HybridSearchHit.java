package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class HybridSearchHit implements Serializable {

    private String documentId;

    private String content;

    private String sourceType;

    private Double score;

    private Double rerankScore;

    private Double finalScore;

    private Map<String, Object> metadata = new HashMap<>();
}
