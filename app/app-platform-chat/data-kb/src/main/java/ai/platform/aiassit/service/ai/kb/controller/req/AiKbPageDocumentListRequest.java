package ai.platform.aiassit.service.ai.kb.controller.req;

import lombok.Data;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

import java.io.Serializable;

@Data
public class AiKbPageDocumentListRequest implements Serializable {

    /**
     * 知识库编码，可选。
     */
    private String kbCode;

    /**
     * 页面搜索关键字，匹配 kbCode/documentCode/documentName/bizKey 等字段。
     */
    @IgnoredQuery
    private String keyword;

    /**
     * 业务类型编码，可选。
     */
    @IgnoredQuery
    private Integer bizTypeCode;

    /**
     * 页面页签：current/draft/history。
     */
    @IgnoredQuery
    private String tab;

    /**
     * 页码，可选。
     */
    private Integer page;

    /**
     * 每页条数，可选。
     */
    private Integer size;
}
