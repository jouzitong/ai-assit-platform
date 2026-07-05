package ai.platform.aiassit.chat;

import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassit.service.ai.api.AiVectorExecutionApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Map;

@MapperScan({
        "ai.platform.aiassit.chat.history.mapper",
        "ai.platform.aiassit.chat.workflow.data.mapper",
        "ai.platform.aiassit.model.mapper"
})
@SpringBootApplication(scanBasePackages = "ai.platform.aiassit")
@EnableFeignClients(basePackageClasses = {
        AiKnowledgeApi.class,
        AiRetrievalExecutionApi.class,
        AiVectorExecutionApi.class,
        RenderInternalApi.class
})
public class PlatformChatApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformChatApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/chat"));
        application.run(args);
    }
}
