package ai.platform.aiassit.conversation.runtime.config;

import ai.platform.aiassit.conversation.runtime.cluster.ConversationRunClusterCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuntimeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withUserConfiguration(ConversationRuntimeConfiguration.class);

    @Test
    void defaultsToLocalModeWithoutRedisInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ConversationRunClusterCoordinator.class);
            assertThat(context.getBean(ConversationRunClusterCoordinator.class).distributed()).isFalse();
            assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
        });
    }

    @Test
    void acceptsExplicitUppercaseLocalMode() {
        contextRunner.withPropertyValues("ai.chat.runtime.mode=LOCAL")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConversationRunClusterCoordinator.class);
                    assertThat(context.getBean(ConversationRuntimeProperties.class).getMode())
                            .isEqualTo(ConversationRuntimeProperties.Mode.LOCAL);
                });
    }
}
