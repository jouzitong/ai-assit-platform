package ai.platform.aiassit.data.virtualization.core.relation;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualRelationBatchSaveRequest {
    private List<VirtualRelationDTO> creates = new ArrayList<>();
    private List<VirtualRelationDTO> updates = new ArrayList<>();
    private List<Long> deletes = new ArrayList<>();
}
