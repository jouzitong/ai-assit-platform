package ai.platform.aiassit.data.virtualization.core.exception;

import ai.platform.aiassit.data.virtualization.api.exception.VirtualDataRuntimeException;

/** 带稳定错误类别的虚拟数据业务异常。 */
public class VirtualDataException extends VirtualDataRuntimeException {

    public VirtualDataException(String code, String message) {
        super(code, message);
    }

    public VirtualDataException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
