package ai.platform.aiassist.service.ai.kb.controller.req;

import lombok.Data;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbSyncRequest implements Serializable {

    /**
     * 可选：指定需要同步的知识库编码列表；为空时表示按查询条件批量同步。
     */
    private List<String> kbCodes = new ArrayList<>();

    /**
     * 可选：指定需要同步的文档编码列表。
     */
    private List<String> documentCodes = new ArrayList<>();

    /**
     * 可选：页面当前页签，用于按状态范围同步。
     */
    @IgnoredQuery
    private String tab;

    /**
     * 可选：页面搜索关键字。
     */
    @IgnoredQuery
    private String keyword;
}
