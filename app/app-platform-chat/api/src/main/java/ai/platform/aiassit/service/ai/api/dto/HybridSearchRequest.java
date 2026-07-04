package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class HybridSearchRequest implements Serializable {

    private ProviderType provider;

    private String kbId;

    private String query;

    private Boolean keywordEnabled = Boolean.TRUE;

    private Boolean vectorEnabled = Boolean.TRUE;

    private Boolean rerankEnabled = Boolean.FALSE;

    private Integer topK = 5;

    private Integer keywordTopK = 5;

    private Integer vectorTopK = 5;

    private RequestMeta meta = new RequestMeta();

    private Map<String, Object> ext = new HashMap<>();
}
