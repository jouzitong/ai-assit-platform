package ai.platform.aiassit.render.data.render.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RenderPageTreeVO {

    private final List<RenderPageCategoryTreeVO> categories = new ArrayList<>();

    private final List<RenderPageManageVO> uncategorizedPages = new ArrayList<>();
}
