package ai.platform.aiassit.agent.runtime;

/**
 * Server-owned artifact delivery profile for one Agent turn.
 *
 * <p>This value must come from a validated execution route. Selecting an Agent
 * only decides who handles the turn; it does not prove that the turn is ready
 * to build an artifact. It must never be inferred from the user's text.</p>
 */
public enum AgentArtifactDelivery {
    STANDARD,
    RENDER_DOCUMENT;

    static final String RENDER_APPLICATION_AGENT_CODE = "dashboard-application-builder";

    public static AgentArtifactDelivery forValidatedRoute(boolean routeApplied,
                                                          String selectedAgentCode) {
        if (routeApplied && selectedAgentCode != null
                && RENDER_APPLICATION_AGENT_CODE.equals(selectedAgentCode.trim())) {
            return RENDER_DOCUMENT;
        }
        return STANDARD;
    }
}
