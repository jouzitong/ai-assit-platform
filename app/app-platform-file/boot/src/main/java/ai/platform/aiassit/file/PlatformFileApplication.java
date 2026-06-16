package ai.platform.aiassit.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class PlatformFileApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformFileApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/file"));
        application.run(args);
    }
}
