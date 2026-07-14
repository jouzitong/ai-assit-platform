package ai.platform.aiassit.data.virtualization.api.exception;

/** 可跨虚拟化 API 边界传播的稳定业务错误。 */
public class VirtualDataRuntimeException extends RuntimeException {

    private final String code;

    public VirtualDataRuntimeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public VirtualDataRuntimeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
