package ai.platform.aiassit.knowledge.manage.req;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbSyncRequest implements Serializable {

    /**
     * 必填：指定同步某个知识库的当前文档。
     */
    private String kbCode;

    /**
     * 可选：指定同步的文档编码列表。
     * <p>为空时同步目标知识库下全部当前文档。</p>
     */
    private List<String> documentCodes = new ArrayList<>();

    /**
     * 是否强制同步，预留给后续跳过未变化文档的场景。
     */
    private Boolean force = Boolean.FALSE;
}
