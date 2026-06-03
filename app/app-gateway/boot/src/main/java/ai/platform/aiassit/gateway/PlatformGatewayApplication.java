package ai.platform.aiassit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "ai.platform.aiassit.gateway")
@EnableFeignClients(basePackages = "ai.platform.aiassit.user.api")
public class PlatformGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformGatewayApplication.class, args);
    }
}
