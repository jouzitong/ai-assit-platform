package ai.platform.aiassit.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ai.platform.aiassist")
public class BootAiEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootAiEngineApplication.class, args);
    }
}
