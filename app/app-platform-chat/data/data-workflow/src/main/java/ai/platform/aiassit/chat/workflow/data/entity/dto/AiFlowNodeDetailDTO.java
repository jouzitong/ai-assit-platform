package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeMessageConfig;
import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeOutputConfig;
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

    private String desc;

    private String modelCode;

    private List<String> skillRefs = new ArrayList<>();

    private List<String> toolRefs = new ArrayList<>();

    private List<String> kbRefs = new ArrayList<>();

    private List<AiNodeMessageConfig> inputConfig = new ArrayList<>();

    private AiNodeOutputConfig outputConfig;

    private List<AiFlowNodeSkillItemDTO> skillItems = new ArrayList<>();
}
