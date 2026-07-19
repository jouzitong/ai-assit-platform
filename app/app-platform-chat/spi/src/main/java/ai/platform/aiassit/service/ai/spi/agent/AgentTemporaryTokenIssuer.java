package ai.platform.aiassit.service.ai.spi.agent;

import org.athena.framework.security.api.model.UserContext;

/** Issues the short-lived credential exposed only to an Agent or Tool child process. */
public interface AgentTemporaryTokenIssuer {

    String issue(UserContext userContext);

    /**
     * Issues a credential bound to one Agent run when the implementation supports run binding.
     *
     * <p>The default preserves compatibility with existing issuers.</p>
     */
    default String issue(UserContext userContext, String agentRunId) {
        return issue(userContext);
    }
}
