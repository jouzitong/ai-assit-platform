package ai.platform.aiassist.service.ai.api.constant;

/**
 * AI 知识库业务异常子码。
 *
 * <p>编码规范：YY_XX_####
 * <ul>
 *     <li>YY: 大方向（51=AI 知识库域）</li>
 *     <li>XX: 小方向（01=必填缺失，02=取值非法，03=资源不存在，04=状态或约束冲突，05=外部同步异常，06=系统处理异常）</li>
 *     <li>####: 具体业务编号</li>
 * </ul>
 */
public interface AiKbBizCodeConstant {

    // 51_01_xxxx 必填参数缺失
    Integer REQUIRED_DTO = 51_01_0001;
    Integer REQUIRED_KB_CODE_AND_DOCUMENT_CODE = 51_01_0002;
    Integer REQUIRED_DOCUMENT_ID = 51_01_0003;
    Integer REQUIRED_KB_ID = 51_01_0004;
    Integer REQUIRED_DOCUMENT_TYPE = 51_01_0005;
    Integer REQUIRED_SOURCE_KEY = 51_01_0006;
    Integer REQUIRED_CONTENT = 51_01_0007;
    Integer REQUIRED_STORE_EXT = 51_01_0008;
    Integer REQUIRED_KB_NAME = 51_01_0009;
    Integer REQUIRED_SOURCE_TYPE = 51_01_0010;

    // 51_02_xxxx 参数取值非法
    Integer INVALID_KB_SOURCE_TYPE = 51_02_0001;
    Integer INVALID_DOCUMENT_SOURCE_TYPE = 51_02_0002;

    // 51_03_xxxx 资源不存在
    Integer DOCUMENT_NOT_FOUND = 51_03_0001;
    Integer CURRENT_DOCUMENT_NOT_FOUND = 51_03_0002;
    Integer KB_STORE_NOT_FOUND = 51_03_0003;

    // 51_04_xxxx 状态或约束冲突
    Integer CURRENT_DOCUMENT_CONTENT_MISSING = 51_04_0001;
    Integer KB_STORE_EXISTS = 51_04_0002;

    // 51_05_xxxx 外部同步异常
    Integer PROVIDER_UPSERT_NOT_ACCEPTED = 51_05_0001;
    Integer PROVIDER_UPSERT_FAILED = 51_05_0002;

    // 51_06_xxxx 系统处理异常
    Integer CHECKSUM_CALCULATE_FAILED = 51_06_0001;
}
