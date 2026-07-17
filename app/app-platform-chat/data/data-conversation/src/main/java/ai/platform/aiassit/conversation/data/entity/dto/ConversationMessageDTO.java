package ai.platform.aiassit.conversation.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 会话消息 DTO。
 *
 * <p>用于承载会话中的单条消息数据，包括消息归属、角色、类型、状态、内容以及扩展信息等。</p>
 */
public class ConversationMessageDTO extends BaseDTO {

    /**
     * 消息编码。
     */
    private String messageCode;

    /**
     * 轮次编码。
     */
    private String roundCode;

    /**
     * 会话编码。
     */
    private String sessionCode;

    /**
     * 消息角色，例如 user、assistant、system 等。
     */
    private String role;

    /**
     * 执行主体类型，用于区分消息来源主体，例如用户、AI、工具、系统等。
     */
    private String actorType;

    /**
     * 消息类型，例如普通消息、进度消息、思考摘要、最终结果、错误提示等。
     */
    private String messageType;

    /**
     * 展示级别，用于控制消息在前端的展示层级或可见范围。
     */
    private String displayLevel;

    /**
     * 内容格式，例如 text、markdown、json、html 等。
     */
    private String contentFormat;

    /**
     * 父消息编码，用于构建消息层级关系。
     */
    private String parentMessageCode;

    /**
     * 来源消息编码，用于记录当前消息关联或派生自哪条原始消息。
     */
    private String sourceMessageCode;

    /**
     * 消息状态，例如初始化、处理中、完成、失败等。
     */
    private String status;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 排序号，用于控制同一会话或同一轮次下消息的展示顺序。
     */
    private Integer sortNo;

    /**
     * 扩展 JSON，用于存储额外的业务扩展信息。
     */
    private String extJson;
}
