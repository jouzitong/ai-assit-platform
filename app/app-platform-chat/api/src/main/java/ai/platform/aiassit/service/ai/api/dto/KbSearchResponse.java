package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class KbSearchResponse implements Serializable {

    /** 本地知识库业务编码。 */
    private String kbCode;

    /**
     * @deprecated 请改用 {@link #kbCode}。为了兼容历史客户端，服务端返回的值与 kbCode 相同，
     * 不会返回 Provider/RAGFlow 的真实知识库 ID。
     */
    @Deprecated(since = "2026-07", forRemoval = true)
    private String kbId;
    /** Provider 返回的总命中数量 */
    private Integer total;
    /** 检索命中结果列表 */
    private List<KbSearchItem> items = new ArrayList<>();
}
