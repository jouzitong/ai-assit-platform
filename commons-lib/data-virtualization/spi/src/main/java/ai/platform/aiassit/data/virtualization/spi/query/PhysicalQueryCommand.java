package ai.platform.aiassit.data.virtualization.spi.query;

public record PhysicalQueryCommand(
        String requestId,
        String planId,
        String taskId,
        String sourceKey,
        PhysicalQuerySpec querySpec,
        int maxRows,
        int timeoutMs
) {
}
