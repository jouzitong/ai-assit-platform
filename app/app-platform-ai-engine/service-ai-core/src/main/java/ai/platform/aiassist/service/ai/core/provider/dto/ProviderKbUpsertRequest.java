package ai.platform.aiassist.service.ai.core.provider.dto;

import ai.platform.aiassist.service.ai.api.dto.KbDocument;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProviderKbUpsertRequest {
    private String kbId;
    private List<KbDocument> documents = new ArrayList<>();
    private RequestMeta meta;
}
