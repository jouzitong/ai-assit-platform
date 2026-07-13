package ai.platform.aiassit.data.virtualization.core.exception;

/** 带稳定错误类别的虚拟数据业务异常。 */
public class VirtualDataException extends RuntimeException {
    private final String code;

    public VirtualDataException(String code, String message) {
        super(message);
        this.code = code;
    }

    public VirtualDataException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
