package ai.platform.aiassit.db.engine.executor.spi.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 协议无关的数据读取命令。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataReadCommand {

    /** 数据源内已注册资源的相对路径或资源标识。 */
    private String resource;

    @Builder.Default
    private Map<String, Object> parameters = new LinkedHashMap<>();

    private Integer page;

    private Integer pageSize;
}
