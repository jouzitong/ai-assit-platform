package ai.platform.aiassit.conversation.workflow.tool;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点运行时可用的工具集合。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class ScopedToolSet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, WorkflowTool<?, ?>> tools = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public <I, O> WorkflowTool<I, O> get(String code) {
        return (WorkflowTool<I, O>) tools.get(code);
    }
}
