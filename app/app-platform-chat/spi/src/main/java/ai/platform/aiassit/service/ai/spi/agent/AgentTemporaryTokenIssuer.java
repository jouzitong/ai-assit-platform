package ai.platform.aiassit.service.ai.spi.agent;

import org.athena.framework.security.api.model.UserContext;

/** Issues the short-lived credential exposed only to an Agent or Tool child process. */
public interface AgentTemporaryTokenIssuer {

    String issue(UserContext userContext);
}
