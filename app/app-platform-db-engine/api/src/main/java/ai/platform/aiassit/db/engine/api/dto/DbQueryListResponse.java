package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryListResponse {

    private List<Map<String, Object>> list = new ArrayList<>();

    private PageInfo pageInfo = new PageInfo();

    private Map<String, Object> summary = new LinkedHashMap<>();

    @Data
    public static class PageInfo {

        private Long total = 0L;

        private Integer size = 10;

        private Integer page = 1;
    }
}
