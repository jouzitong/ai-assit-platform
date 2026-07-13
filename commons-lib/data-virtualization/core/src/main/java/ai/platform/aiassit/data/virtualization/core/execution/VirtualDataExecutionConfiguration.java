package ai.platform.aiassit.data.virtualization.core.execution;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class VirtualDataExecutionConfiguration {
    @Bean(name = "virtualDataTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualDataTaskExecutor() {
        int parallelism = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "virtual-data-task-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(parallelism, threadFactory);
    }
}
