package ai.platform.aiassit.service.ai.spi.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringExclude;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-run model connection resolved by the trusted Java control plane. */
@Data
public class AgentModelConnection implements Serializable {
    private String modelCode;
    private String model;
    private String baseUrl;
    @JsonIgnore
    @ToStringExclude
    private String apiKey;
    private Map<String, Object> settings = new LinkedHashMap<>();
}
