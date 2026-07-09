package ai.platform.aiassit.user.errcode.data.service.impl;

import ai.platform.aiassit.user.errcode.data.convert.ErrCodeI18nConvert;
import ai.platform.aiassit.user.errcode.data.entity.ErrCodeI18nEntity;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeI18nDTO;
import ai.platform.aiassit.user.errcode.data.entity.req.ErrCodeI18nQueryRequest;
import ai.platform.aiassit.user.errcode.data.mapper.ErrCodeI18nMapper;
import ai.platform.aiassit.user.errcode.data.service.ErrCodeI18nService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ErrCodeI18nServiceImpl
        extends BaseMapperService<ErrCodeI18nEntity, ErrCodeI18nMapper, ErrCodeI18nDTO>
        implements ErrCodeI18nService {

    private final ErrCodeI18nConvert errCodeI18nConvert;

    public ErrCodeI18nServiceImpl(ErrCodeI18nConvert errCodeI18nConvert) {
        this.errCodeI18nConvert = errCodeI18nConvert;
    }

    @Override
    protected IConvert<ErrCodeI18nEntity, ErrCodeI18nDTO> convert() {
        return errCodeI18nConvert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<ErrCodeI18nEntity> buildQuery(Query query) {
        QueryWrapper<ErrCodeI18nEntity> qw = super.buildQuery(query);
        if (query instanceof ErrCodeI18nQueryRequest request) {
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = request.getKeyword();
                qw.and(wrapper -> wrapper.like("err_code", keyword)
                        .or()
                        .like("locale", keyword)
                        .or()
                        .like("message_template", keyword)
                        .or()
                        .like("description", keyword));
            }
            qw.orderByDesc("update_time").orderByDesc("id");
        }
        return qw;
    }
}
