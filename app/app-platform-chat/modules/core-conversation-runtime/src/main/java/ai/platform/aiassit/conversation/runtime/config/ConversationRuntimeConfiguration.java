package ai.platform.aiassit.conversation.runtime.config;

import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.cluster.LocalConversationRunClusterCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConversationRuntimeProperties.class)
public class ConversationRuntimeConfiguration {

    @Bean
    @ConditionalOnExpression("'${ai.chat.runtime.mode:local}'.toLowerCase() != 'redis'")
    public ConversationRunClusterCoordinator localConversationRunClusterCoordinator(
            ConversationRuntimeProperties properties) {
        return new LocalConversationRunClusterCoordinator(properties.resolvedNodeId());
    }
}
