package ai.platform.aiassit.service.ai.api.constant;

/**
 * AI 聊天业务异常子码。
 *
 * <p>编码规范：YY_XX_####
 * <ul>
 *     <li>YY: 大方向（53=AI 聊天域）</li>
 *     <li>XX: 小方向（01=必填缺失，03=资源不存在）</li>
 *     <li>####: 具体业务编号</li>
 * </ul>
 */
public interface AiChatBizCodeConstant {

    // 53_01_xxxx 必填参数缺失
    Integer REQUIRED_MESSAGE = 53_01_0001;
    Integer REQUIRED_QUERY_COMMAND = 53_01_0002;
    Integer REQUIRED_SESSION_CODE = 53_01_0003;
    Integer REQUIRED_ROUND_CODE = 53_01_0004;
    Integer REQUIRED_PROVIDER = 53_01_0005;
    Integer REQUIRED_BASE_URL = 53_01_0006;
    Integer REQUIRED_API_MODEL = 53_01_0007;
    Integer REQUIRED_API_KEY = 53_01_0008;

    // 53_03_xxxx 资源不存在
    Integer CONVERSATION_NOT_FOUND = 53_03_0001;
    Integer CONVERSATION_ROUND_NOT_FOUND = 53_03_0002;
    Integer AI_CHAT_SERVICE_NOT_FOUND = 53_03_0003;
    Integer KNOWLEDGE_SERVICE_NOT_FOUND = 53_03_0004;

    // 53_06_xxxx 系统处理异常
    Integer WORKFLOW_EXECUTION_FAILED = 53_06_0001;
}
