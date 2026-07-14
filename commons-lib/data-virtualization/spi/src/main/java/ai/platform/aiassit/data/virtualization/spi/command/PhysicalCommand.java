package ai.platform.aiassit.data.virtualization.spi.command;

public record PhysicalCommand(
        String requestId,
        String planId,
        String taskId,
        String sourceKey,
        PhysicalCommandSpec commandSpec
) {
}
