package ai.platform.aiassit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Map;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "ai.platform.aiassit.gateway")
@EnableFeignClients(basePackages = "ai.platform.aiassit.user.api")
public class PlatformGatewayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformGatewayApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/gateway"));
        application.run(args);
    }
}
