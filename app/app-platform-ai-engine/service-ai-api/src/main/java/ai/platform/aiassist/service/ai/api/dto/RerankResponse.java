package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class RerankResponse implements Serializable {

    /** 请求唯一标识 */
    private String requestId;
    /** 实际执行模型 */
    private String model;
    /** 重排结果列表 */
    private List<RerankItem> items = new ArrayList<>();
}
