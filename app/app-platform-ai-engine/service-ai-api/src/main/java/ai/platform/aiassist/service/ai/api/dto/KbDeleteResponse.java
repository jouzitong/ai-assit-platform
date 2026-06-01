package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;



@Data
public class KbDeleteResponse implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** 实际删除文档数量 */
    private Integer deleted = 0;
}
