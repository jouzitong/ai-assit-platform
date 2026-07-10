package ai.platform.aiassit.render.data.render.entity.vo;

import lombok.Data;

import java.util.Map;

@Data
public class RenderPageContentVO {

    private String pageCode;

    private Map<String, Object> content;
}
