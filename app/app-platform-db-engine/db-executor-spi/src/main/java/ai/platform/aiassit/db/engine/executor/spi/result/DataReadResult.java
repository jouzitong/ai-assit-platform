package ai.platform.aiassit.db.engine.executor.spi.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 不同协议读取结果归一后的行数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataReadResult {

    @Builder.Default
    private List<Map<String, Object>> records = new ArrayList<>();

    private Long total;

    private Integer statusCode;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
