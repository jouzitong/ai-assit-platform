package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * AI 知识库客户端类型。
 *
 * <p>用于在运行时选择创建和调用知识库客户端的 Driver，不表示知识库供应商。
 * 连接地址、凭据、远端知识库标识及 Provider 专属参数均由知识库配置实体提供。</p>
 */
@Getter
public enum AiKnowledgeClientType implements IEnum {

    /** 阿里云百炼知识库客户端。 */
    BAILIAN(1, "百炼知识库客户端"),
    /** RAGFlow 知识库客户端。 */
    RAGFLOW(2, "RAGFlow知识库");

    @JsonValue
    private final int code;

    private final String name;

    AiKnowledgeClientType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
