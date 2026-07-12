package ai.platform.aiassit.knowledge.manage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 知识库同步任务的本地异步执行器。 */
@Configuration
public class AiKbSyncTaskConfiguration {

    public static final String EXECUTOR_NAME = "aiKbSyncTaskExecutor";

    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor aiKbSyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-kb-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
