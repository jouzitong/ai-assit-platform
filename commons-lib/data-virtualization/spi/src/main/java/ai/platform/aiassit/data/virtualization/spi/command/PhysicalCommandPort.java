package ai.platform.aiassit.data.virtualization.spi.command;

/** Executes a database-independent physical write command. */
public interface PhysicalCommandPort {

    PhysicalCommandResult execute(PhysicalCommand command);
}
