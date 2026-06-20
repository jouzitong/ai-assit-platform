package ai.platform.aiassist.service.ai.kb.controller.req;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbSyncRequest implements Serializable {

    /**
     * 可选：指定发布某个知识库的草稿版本。
     *
     * <p>为空时由服务端查询所有 DRAFT 版本并发布。</p>
     */
    private String kbCode;

    /**
     * 可选：指定发布版本 ID。
     *
     * <p>优先级高于 kbCode/versionNo；为空时按 kbCode 查询当前 DRAFT 版本。</p>
     */
    private Long kbVersionId;

    /**
     * 可选：指定发布版本号。
     *
     * <p>需要与 kbCode 一起使用；为空时按 kbCode 查询当前 DRAFT 版本。</p>
     */
    private Integer versionNo;
}
