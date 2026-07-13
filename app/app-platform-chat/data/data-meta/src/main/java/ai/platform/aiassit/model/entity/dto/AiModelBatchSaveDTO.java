package ai.platform.aiassit.model.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiModelBatchSaveDTO {
    private Long clientId;
    private List<String> apiModels;
}
