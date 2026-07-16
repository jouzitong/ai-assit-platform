package ai.platform.aiassit.service.ai.spi.tool;

/** Resolves a secret reference at invocation time. Secret values must never enter Tool definitions. */
public interface ToolSecretResolver {
    boolean supports(String secretRef);

    String resolve(String secretRef, ToolInvocationPrincipal principal);
}
