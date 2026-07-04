package ai.platform.aiassit.chat.workflow.data.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiFlowDetailDTO {

    private Long workflowId;

    private String workflowKey;

    private String workflowCode;

    private String workflowName;

    private String workflowScene;

    private String workflowStatus;

    private List<String> workflowTags = new ArrayList<>();

    private Long configId;

    private String configCode;

    private List<AiFlowNodeDetailDTO> nodeDefinitions = new ArrayList<>();
}
