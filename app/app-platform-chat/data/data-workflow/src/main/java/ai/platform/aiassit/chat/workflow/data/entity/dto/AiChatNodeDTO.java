package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeMessageConfig;
import ai.platform.aiassit.chat.workflow.data.entity.config.AiNodeOutputConfig;
import ai.platform.aiassit.chat.workflow.data.enums.AiExecuteType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatNodeDTO extends BaseDTO {

    private String code;

    private String name;

    private String desc;

    private AiExecuteType executeType;

    private String modelCode;

    private List<String> skillRefs = new ArrayList<>();

    private List<String> toolRefs = new ArrayList<>();

    private List<String> kbRefs = new ArrayList<>();

    private List<AiNodeMessageConfig> inputConfig = new ArrayList<>();

    private AiNodeOutputConfig outputConfig;

    private Boolean enabled = Boolean.TRUE;

    private String remark;
}
