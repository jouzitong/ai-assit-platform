package ai.platform.aiassit.agent.runtime;

import org.springframework.util.StringUtils;

/** Server-authorized target for one chat turn. */
public record AgentTarget(String type, String entryCode, String agentCode, Integer agentVersion) {
    public static final String TYPE_AGENT = "AGENT";

    public static AgentTarget homeChat() {
        return new AgentTarget(TYPE_AGENT, "HOME_CHAT", null, null);
    }

    public static AgentTarget explicit(String agentCode, Integer agentVersion) {
        return new AgentTarget(TYPE_AGENT, null, agentCode, agentVersion);
    }

    public boolean explicit() {
        return StringUtils.hasText(agentCode);
    }
}
