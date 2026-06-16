package ai.platform.aiassit.db.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
//@MapperScan("com.zhouzhitong.test.mybatis.mapper")
public class PlatformDbEngineApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformDbEngineApplication.class);
        application.setDefaultProperties(Map.of("athena.log.dir", "./logs/ai-assit/dbEngine"));
        application.run(args);
    }
}
