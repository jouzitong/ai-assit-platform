package ai.platform.aiassit.chat.workflow.data.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiFlowWorkflowFormDTO {

    private Long id;

    private String key;

    private String code;

    private String name;

    private String type;

    private Boolean enabled = Boolean.TRUE;

    private String scene;

    private List<String> tags = new ArrayList<>();
}
