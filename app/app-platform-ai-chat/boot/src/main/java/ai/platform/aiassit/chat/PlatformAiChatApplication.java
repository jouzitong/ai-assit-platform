package ai.platform.aiassit.chat;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.AiVectorExecutionApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {
        AiChatExecutionApi.class,
        AiMetaQueryApi.class,
        AiVectorExecutionApi.class,
        AiKnowledgeBaseExecutionApi.class
})
public class PlatformAiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformAiChatApplication.class, args);
    }
}
