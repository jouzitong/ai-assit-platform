package ai.platform.aiassit.chat;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.AiVectorExecutionApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {
        AiChatExecutionApi.class,
        AiMetaQueryApi.class,
        AiVectorExecutionApi.class,
        AiKnowledgeBaseExecutionApi.class
})
public class PlatformAiChatApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformAiChatApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/aiChat"));
        application.run(args);
    }
}
