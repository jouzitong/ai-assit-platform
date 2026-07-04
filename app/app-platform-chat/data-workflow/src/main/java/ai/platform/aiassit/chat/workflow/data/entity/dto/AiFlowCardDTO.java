package ai.platform.aiassit.chat.workflow.data.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiFlowCardDTO {

    private Long id;

    private String key;

    private String code;

    private String name;

    private String type;

    private String scene;

    private String nodes;

    private String status;

    private List<String> tags = new ArrayList<>();
}
