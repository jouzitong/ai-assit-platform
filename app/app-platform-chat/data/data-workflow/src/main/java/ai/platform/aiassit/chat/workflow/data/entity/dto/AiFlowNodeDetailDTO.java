package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowFieldDefinition;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeConfigItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiFlowNodeDetailDTO {

    private Long id;

    private String configCode;

    private String nodeCode;

    private String key;

    private String name;

    private String type;

    private String status;

    private String mode;

    private String nextCode;

    private Integer sort;

    private String summary;

    private List<WorkflowFieldDefinition> inputDefinitions = new ArrayList<>();

    private List<WorkflowFieldDefinition> outputDefinitions = new ArrayList<>();

    private List<WorkflowNodeConfigItem> configItems = new ArrayList<>();

    private List<AiFlowNodeSkillItemDTO> skillItems = new ArrayList<>();
}
