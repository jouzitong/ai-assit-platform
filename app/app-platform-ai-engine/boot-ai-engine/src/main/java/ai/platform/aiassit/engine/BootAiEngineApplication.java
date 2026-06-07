package ai.platform.aiassit.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("ai.platform.aiassist")
@SpringBootApplication(scanBasePackages = "ai.platform.aiassist")
public class BootAiEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootAiEngineApplication.class, args);
    }
}
