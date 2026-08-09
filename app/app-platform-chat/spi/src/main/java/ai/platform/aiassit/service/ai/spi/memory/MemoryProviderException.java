package ai.platform.aiassit.service.ai.spi.memory;

/** Stable provider failure carrying whether a write outcome is uncertain. */
public class MemoryProviderException extends RuntimeException {

    private final String errorCode;
    private final boolean uncertain;

    public MemoryProviderException(String errorCode, String message, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.uncertain = uncertain;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isUncertain() {
        return uncertain;
    }
}
