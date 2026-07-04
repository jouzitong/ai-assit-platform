package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.ProviderType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class RerankRequest implements Serializable {

    /** 指定 AI 平台，允许为空（由实现层路由） */
    private ProviderType provider;
    /** 重排模型名称 */
    private String model;
    /** 用户查询 */
    private String query;
    /** 候选文本列表 */
    private List<String> candidates = new ArrayList<>();
    /** 返回前 N 条结果 */
    private Integer topN;
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}
