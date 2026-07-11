package ai.platform.aiassit.db.engine.core.controller.resp;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据表只读预览结果。 */
@Data
public class DbAccessTableDataPreviewResponse {

    private String sourceKey;

    private String tableName;

    private Integer page;

    private Integer pageSize;

    private Boolean hasNext;

    private Long executionMs;

    private List<String> columns = new ArrayList<>();

    private List<Map<String, Object>> records = new ArrayList<>();

    private Map<String, Object> metadata = new LinkedHashMap<>();
}
