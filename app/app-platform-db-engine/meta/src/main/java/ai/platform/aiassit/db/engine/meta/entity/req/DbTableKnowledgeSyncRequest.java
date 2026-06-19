package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;

@Data
public class DbTableKnowledgeSyncRequest {

    /**
     * 数据源唯一标识，必填。
     */
    private String sourceKey;

    /**
     * 数据表名称，非必填。
     * 为空时表示同步当前数据源下的全部数据表；传值时仅同步指定数据表。
     */
    private String tableName;
}
