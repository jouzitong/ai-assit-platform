package ai.platform.aiassit.service.ai.spi.memory.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class MemoryMessagesResponse implements Serializable {
    private List<MemoryMessage> items = new ArrayList<>();
    private long total;
}
