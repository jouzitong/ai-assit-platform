package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class EmbedRequest implements Serializable {

    /** 指定知识库客户端类型，允许为空（由实现层路由） */
    private AiKnowledgeClientType clientType;
    /** 向量模型名称 */
    private String model;
    /** 待向量化文本列表 */
    private List<String> inputs = new ArrayList<>();
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}
