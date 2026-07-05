package ai.platform.aiassit.chat.core.workflow.runtime;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点可见的上下文切片。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class ContextView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, Object> values = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }
}
