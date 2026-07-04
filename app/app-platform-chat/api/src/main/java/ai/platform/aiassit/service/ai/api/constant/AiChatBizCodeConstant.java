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

    // 53_03_xxxx 资源不存在
    Integer CONVERSATION_NOT_FOUND = 53_03_0001;
}
