package ai.platform.aiassist.service.ai.spi.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProviderEmbedRequest {
    private String model;
    private List<String> inputs = new ArrayList<>();
    private RequestMeta meta;
}
