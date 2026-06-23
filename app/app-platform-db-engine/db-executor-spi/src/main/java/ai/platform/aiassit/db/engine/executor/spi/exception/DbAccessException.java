package ai.platform.aiassit.db.engine.executor.spi.exception;

public class DbAccessException extends Exception {

    public DbAccessException(String message) {
        super(message);
    }

    public DbAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
