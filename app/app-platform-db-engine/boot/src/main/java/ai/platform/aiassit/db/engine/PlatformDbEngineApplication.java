package ai.platform.aiassit.db.engine;

import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Map;

@MapperScan({
        "ai.platform.aiassit.db.engine.meta.mapper",
        "ai.platform.aiassit.data.virtualization.data.mapper"
})
@SpringBootApplication(scanBasePackages = "ai.platform.aiassit")
@EnableFeignClients(basePackageClasses = {
        AiKnowledgeApi.class,
        SystemSettingInternalApi.class,
})

public class PlatformDbEngineApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformDbEngineApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/dbEngine"));
        application.run(args);
    }
}
