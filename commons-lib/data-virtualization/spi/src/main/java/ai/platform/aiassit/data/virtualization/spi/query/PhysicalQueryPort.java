package ai.platform.aiassit.data.virtualization.spi.query;

/** Executes a database-independent physical query through the hosting application's DB Engine. */
public interface PhysicalQueryPort {

    PhysicalQueryResult query(PhysicalQueryCommand command);
}
