package ai.platform.aiassist.service.ai.core.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProviderKbDeleteRequest {
    private String kbId;
    private List<String> documentIds = new ArrayList<>();
    private RequestMeta meta;
}
