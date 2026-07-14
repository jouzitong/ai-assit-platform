package ai.platform.aiassit.service.ai.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 百炼知识库运行参数；连接地址和认证信息由请求上下文注入。 */
@Data
@ConfigurationProperties(prefix = "ai.provider.bailian-kb")
public class BailianKnowledgeProperties {

    /** 默认业务空间 ID；请求 ext.workspaceId 优先。 */
    private String workspaceId;

    /** 默认知识库类目 ID */
    private String categoryId = "default";

    /** 默认文件解析器 */
    private String parser = "DASHSCOPE_DOCMIND";

    /** 知识库源类型 */
    private String sourceType = "DATA_CENTER_FILE";

    /** 知识库结构类型 */
    private String structureType = "unstructured";

    /** 知识库存储类型 */
    private String sinkType = "BUILT_IN";

    /** 轮询索引任务间隔（毫秒） */
    private Integer kbPollIntervalMs = 1000;

    /** 等待索引任务完成超时（毫秒） */
    private Integer kbJobTimeoutMs = 120000;

}
