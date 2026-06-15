package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class HybridSearchResponse implements Serializable {

    private String kbId;

    private String query;

    private String retrievalMode;

    private Boolean reranked = Boolean.FALSE;

    private Boolean degraded = Boolean.FALSE;

    private String degradedReason;

    private List<HybridSearchHit> hits = new ArrayList<>();
}
