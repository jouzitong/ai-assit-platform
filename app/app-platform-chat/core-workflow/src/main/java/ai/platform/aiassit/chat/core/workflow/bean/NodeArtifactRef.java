package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点产物引用。
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Data
public class NodeArtifactRef implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String artifactId;

    private String artifactType;

    private String artifactName;

    private Map<String, Object> metadata = new LinkedHashMap<>();
}
