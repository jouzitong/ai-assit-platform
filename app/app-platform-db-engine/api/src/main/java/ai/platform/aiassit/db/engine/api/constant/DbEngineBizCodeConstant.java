package ai.platform.aiassit.db.engine.api.constant;

/**
 * DB 引擎业务异常子码。
 *
 * <p>编码规范：YY_XX_####
 * <ul>
 *     <li>YY: 大方向（55=DB 引擎域）</li>
 *     <li>XX: 小方向（01=必填缺失，02=取值非法，03=资源不存在，05=外部同步失败，06=系统处理失败）</li>
 *     <li>####: 具体业务编号</li>
 * </ul>
 */
public interface DbEngineBizCodeConstant {

    // 55_01_xxxx 必填参数缺失
    Integer REQUIRED_SOURCE_KEY = 55_01_0001;
    Integer REQUIRED_TABLE_NAME = 55_01_0002;
    Integer REQUIRED_IMPORT_FILE = 55_01_0003;

    // 55_02_xxxx 参数取值非法
    Integer INVALID_IMPORT_FORMAT = 55_02_0001;
    Integer INVALID_WORKBOOK_FIELD = 55_02_0002;
    Integer INVALID_BOOLEAN_VALUE = 55_02_0003;
    Integer INVALID_EXPORT_FORMAT = 55_02_0004;

    // 55_03_xxxx 资源不存在
    Integer TABLE_META_NOT_FOUND = 55_03_0001;
    Integer IMPORT_JOB_NOT_FOUND = 55_03_0002;
    Integer SYNC_TABLE_META_NOT_FOUND = 55_03_0003;

    // 55_05_xxxx 外部同步异常
    Integer KB_ID_SETTING_MISSING = 55_05_0001;
    Integer KB_SYNC_FAILED = 55_05_0002;

    // 55_06_xxxx 系统处理异常
    Integer DB_META_UPDATE_FAILED = 55_06_0001;
    Integer TEMPLATE_CONFIG_INVALID = 55_06_0002;
    Integer TEMPLATE_CONFIG_NOT_FOUND = 55_06_0003;
}
