package ai.platform.aiassit.render.data.render.service.impl;

import ai.platform.aiassit.render.data.render.convert.RenderPageCategoryConvert;
import ai.platform.aiassit.render.data.render.entity.RenderPageCategoryEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageCategoryQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageCategoryTreeVO;
import ai.platform.aiassit.render.data.render.mapper.RenderPageCategoryMapper;
import ai.platform.aiassit.render.data.render.service.RenderPageCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RenderPageCategoryServiceImpl
        extends BaseMapperService<RenderPageCategoryEntity, RenderPageCategoryMapper, RenderPageCategoryDTO>
        implements RenderPageCategoryService {

    private final RenderPageCategoryConvert convert;

    public RenderPageCategoryServiceImpl(RenderPageCategoryConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<RenderPageCategoryEntity, RenderPageCategoryDTO> convert() {
        return convert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<RenderPageCategoryEntity> buildQuery(Query query) {
        QueryWrapper<RenderPageCategoryEntity> wrapper = super.buildQuery(query);
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        if (query instanceof RenderPageCategoryQueryRequest request && StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w.like("code", keyword)
                    .or()
                    .like("name", keyword)
                    .or()
                    .like("path", keyword));
        }
        return wrapper;
    }

    @Override
    public RenderPageCategoryDTO queryByCode(String code) {
        RenderPageCategoryEntity entity = baseMapper.selectByCode(code);
        return entity == null ? null : convert.toDTO(entity);
    }

    @Override
    public List<RenderPageCategoryTreeVO> queryTree(RenderPageCategoryQueryRequest request) {
        List<RenderPageCategoryDTO> categories = queryAll(request == null ? new RenderPageCategoryQueryRequest() : request);
        Map<String, RenderPageCategoryTreeVO> nodeMap = new LinkedHashMap<>();
        for (RenderPageCategoryDTO category : categories) {
            RenderPageCategoryTreeVO node = new RenderPageCategoryTreeVO();
            node.setId(category.getId());
            node.setCode(category.getCode());
            node.setName(category.getName());
            node.setParentCode(category.getParentCode());
            node.setPath(category.getPath());
            node.setSortNo(category.getSortNo());
            node.setEnabled(category.getEnabled());
            nodeMap.put(node.getCode(), node);
        }

        List<RenderPageCategoryTreeVO> roots = new ArrayList<>();
        for (RenderPageCategoryTreeVO node : nodeMap.values()) {
            RenderPageCategoryTreeVO parent = nodeMap.get(node.getParentCode());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<RenderPageCategoryTreeVO> nodes) {
        nodes.sort(Comparator.comparing(RenderPageCategoryTreeVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(RenderPageCategoryTreeVO::getCode, Comparator.nullsLast(String::compareTo)));
        for (RenderPageCategoryTreeVO node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
