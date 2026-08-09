package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;

import java.io.Serializable;

/** Acknowledgement of asynchronous extraction; accepted does not mean extraction completed. */
@Data
public class MemoryWriteResponse implements Serializable {
    private boolean accepted;
    private String providerMessageId;
    private String providerStatus;
}
