package ai.platform.aiassit.service.ai.spi.tool;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable published Tool definition exposed to the run plane. */
@Value
@Builder
public class PublishedToolDefinition {
    String toolCode;
    Integer toolVersion;
    String adapterType;
    String checksum;
    @Builder.Default
    Map<String, Object> definition = new LinkedHashMap<>();
}
