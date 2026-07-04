package ai.platform.aiassist.service.ai.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiAgentProperties.class)
@ConditionalOnProperty(prefix = "ai.provider.ai-agent", name = "enabled", havingValue = "true")
public class AiAgentProviderConfiguration {
}
