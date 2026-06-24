package ai.platform.aiassit.render.data.render.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RenderPageCategoryTreeVO {

    private Long id;

    private String code;

    private String name;

    private String parentCode;

    private String path;

    private Integer sortNo;

    private Boolean enabled;

    private final List<RenderPageCategoryTreeVO> children = new ArrayList<>();

    private final List<RenderPageManageVO> pages = new ArrayList<>();
}
