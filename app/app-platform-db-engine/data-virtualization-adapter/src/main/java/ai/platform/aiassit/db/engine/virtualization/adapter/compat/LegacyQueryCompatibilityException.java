package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

/**
 * 旧 DbQuery 协议转换或结果整形失败。
 *
 * <p>异常码由上层兼容门面映射为统一 Web 错误，本层不依赖 Web 或虚拟内核异常类型。</p>
 */
public class LegacyQueryCompatibilityException extends RuntimeException {

    private final String code;

    public LegacyQueryCompatibilityException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
