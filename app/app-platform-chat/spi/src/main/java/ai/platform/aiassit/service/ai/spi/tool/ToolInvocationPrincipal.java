package ai.platform.aiassit.service.ai.spi.tool;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/** Authenticated identity supplied by the Tool Gateway transport. */
@Value
@Builder
public class ToolInvocationPrincipal {
    Long userId;
    @Builder.Default
    Set<String> roles = Set.of();
    @Builder.Default
    Set<String> permissions = Set.of();
    String traceId;
}
