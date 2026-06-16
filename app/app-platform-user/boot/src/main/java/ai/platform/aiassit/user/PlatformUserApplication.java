package ai.platform.aiassit.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class PlatformUserApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformUserApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/user"));
        application.run(args);
    }
}
