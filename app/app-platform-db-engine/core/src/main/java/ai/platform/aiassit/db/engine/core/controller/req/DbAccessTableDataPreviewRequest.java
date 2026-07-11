package ai.platform.aiassit.db.engine.core.controller.req;

import lombok.Data;

/** 数据表只读预览请求。 */
@Data
public class DbAccessTableDataPreviewRequest {

    private String sourceKey;

    private String tableName;

    private Integer page;

    private Integer pageSize;
}
