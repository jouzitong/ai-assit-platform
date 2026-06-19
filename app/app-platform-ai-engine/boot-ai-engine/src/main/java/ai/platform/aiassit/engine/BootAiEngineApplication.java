package ai.platform.aiassit.engine;

import lombok.extern.slf4j.Slf4j;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@MapperScan({"ai.platform.aiassist.service.ai.meta.mapper", "ai.platform.aiassist.service.ai.kb.mapper"})
@SpringBootApplication(scanBasePackages = "ai.platform.aiassist")
@Slf4j
public class BootAiEngineApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BootAiEngineApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/aiEngine"));
        application.run(args);
    }

    @Bean
    public ConfigurationCustomizer defaultEnumTypeHandlerCustomizer() {
        return configuration -> {
            log.info("load mybatis DefaultEnumTypeHandler");
            configuration.setDefaultEnumTypeHandler(DefaultEnumTypeHandler.class);
        };
    }
}
