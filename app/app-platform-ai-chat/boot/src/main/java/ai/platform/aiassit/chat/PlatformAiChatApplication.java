package ai.platform.aiassit.chat;

import ai.platform.aiassit.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassit.service.ai.api.AiVectorExecutionApi;
import ai.platform.aiassit.render.api.RenderInternalApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Map;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {
        AiChatExecutionApi.class,
        AiMetaQueryApi.class,
        AiVectorExecutionApi.class,
        AiKnowledgeApi.class,
        RenderInternalApi.class
})
public class PlatformAiChatApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformAiChatApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/aiChat"));
        application.run(args);
    }
}
