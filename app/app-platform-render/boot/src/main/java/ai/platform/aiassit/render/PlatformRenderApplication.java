package ai.platform.aiassit.render;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class PlatformRenderApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformRenderApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/render"));
        application.run(args);
    }
}
