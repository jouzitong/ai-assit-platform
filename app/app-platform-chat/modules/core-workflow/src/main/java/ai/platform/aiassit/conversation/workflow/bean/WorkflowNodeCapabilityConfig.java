package ai.platform.aiassit.conversation.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点 Capability 挂接配置。
 *
 * <p>Capability 负责在节点请求 AI 前补充可复用上下文，例如知识库、系统文档、表结构等。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeCapabilityConfig implements Serializable {

    private String code;

    private Boolean required = Boolean.FALSE;

    private Integer sort = 100;

    private Map<String, Object> options = new LinkedHashMap<>();

    private Map<String, Object> ext = new LinkedHashMap<>();
}
