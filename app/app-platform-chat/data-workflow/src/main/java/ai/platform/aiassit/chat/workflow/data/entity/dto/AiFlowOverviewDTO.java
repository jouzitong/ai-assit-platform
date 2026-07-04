package ai.platform.aiassit.chat.workflow.data.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiFlowOverviewDTO {

    private List<AiFlowCardDTO> workflows = new ArrayList<>();

    private List<AiFlowCardDTO> nodes = new ArrayList<>();

    private List<AiFlowCardDTO> skills = new ArrayList<>();
}
