package ai.platform.aiassit.service.ai.spi.memory.dto;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.io.Serializable;

/** Base request carrying transient server-resolved Provider connection context. */
@Data
public abstract class ProviderMemoryRequest implements Serializable {
    private RequestMeta meta;
}
