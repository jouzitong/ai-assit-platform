package ai.platform.aiassit.service.ai.spi.tool;

import java.util.Map;

/** Verifies an out-of-band approval for a high-risk invocation. */
public interface ToolApprovalVerifier {
    boolean verify(PublishedToolDefinition tool,
                   ToolInvocationPrincipal principal,
                   String approvalToken,
                   Map<String, Object> arguments);
}
