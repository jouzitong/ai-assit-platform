package ai.platform.aiassit.conversation.runtime.config;

import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import ai.platform.aiassit.conversation.runtime.cluster.RedisConversationRunClusterCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnExpression("'${ai.chat.runtime.mode:local}'.toLowerCase() == 'redis'")
public class RedisConversationRuntimeConfiguration {

    @Bean
    public RedisMessageListenerContainer conversationRuntimeRedisListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public ConversationRunClusterCoordinator redisConversationRunClusterCoordinator(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RedisMessageListenerContainer conversationRuntimeRedisListenerContainer,
            ConversationRuntimeProperties properties) {
        return new RedisConversationRunClusterCoordinator(
                redis,
                objectMapper,
                conversationRuntimeRedisListenerContainer,
                properties
        );
    }
}
