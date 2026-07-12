package ai.platform.aiassit.conversation.runtime.config;

import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.cluster.LocalConversationRunClusterCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ConversationRuntimeProperties.class)
public class ConversationRuntimeConfiguration {

    public static final String RUN_EXECUTOR = "conversationRunExecutor";

    @Bean(name = RUN_EXECUTOR)
    public ThreadPoolTaskExecutor conversationRunExecutor(ConversationRuntimeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("conversation-run-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnExpression("'${ai.chat.runtime.mode:local}'.toLowerCase() != 'redis'")
    public ConversationRunClusterCoordinator localConversationRunClusterCoordinator(
            ConversationRuntimeProperties properties) {
        return new LocalConversationRunClusterCoordinator(properties.resolvedNodeId());
    }
}
