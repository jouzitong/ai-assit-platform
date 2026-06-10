package ai.platform.aiassit.chat.core.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Component
@Data
@ConfigurationProperties(prefix = "ai.chat.workflow")
public class WorkflowProperties {

    private String defaultApiModel = "qwen-math-turbo";

    /**
     * QueryPlanningNode 结构化返回校验失败后的最大重试次数。
     * 超过该次数后直接中断整个工作流。
     */
    private Integer planningStructureMaxRetry = 5;

}
