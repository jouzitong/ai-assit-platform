package ai.platform.aiassit.agent.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AgentControlPlaneWebConfiguration implements WebMvcConfigurer {

    private final AgentControlPlaneAuthorizationInterceptor authorizationInterceptor;

    public AgentControlPlaneWebConfiguration(AgentControlPlaneAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns(
                        "/api/v1/ai/agents/**",
                        "/api/v1/ai/agent-entries/**",
                        "/api/v1/ai/skills/**",
                        "/api/v1/ai/tools/**",
                        "/api/v1/ai/workflows/**");
    }
}
