package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class KbSearchResponse implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** Provider 返回的总命中数量 */
    private Integer total;
    /** 检索命中结果列表 */
    private List<KbSearchItem> items = new ArrayList<>();
}
