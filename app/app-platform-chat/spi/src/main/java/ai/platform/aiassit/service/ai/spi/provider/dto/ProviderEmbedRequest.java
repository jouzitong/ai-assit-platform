package ai.platform.aiassit.service.ai.spi.provider.dto;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProviderEmbedRequest {
    private String model;
    private List<String> inputs = new ArrayList<>();
    private RequestMeta meta;
}
