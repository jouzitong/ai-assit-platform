package ai.platform.aiassit.user.security.management.service.impl;

import ai.platform.aiassit.user.security.management.convert.SecRoleManagementConvert;
import ai.platform.aiassit.user.security.management.entity.SecRoleManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecRoleQueryRequest;
import ai.platform.aiassit.user.security.management.mapper.SecRoleManagementMapper;
import ai.platform.aiassit.user.security.management.service.SecRoleManagementService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SecRoleManagementServiceImpl
        extends BaseMapperService<SecRoleManagementEntity, SecRoleManagementMapper, SecRoleDTO>
        implements SecRoleManagementService {

    private final SecRoleManagementConvert convert;

    public SecRoleManagementServiceImpl(SecRoleManagementConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<SecRoleManagementEntity, SecRoleDTO> convert() {
        return convert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<SecRoleManagementEntity> buildQuery(Query query) {
        QueryWrapper<SecRoleManagementEntity> wrapper = super.buildQuery(query);
        if (query instanceof SecRoleQueryRequest request && StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(item -> item.like("role_code", keyword).or().like("role_name", keyword));
        }
        return wrapper.orderByDesc("id");
    }

    @Override
    public SecRoleDTO add(SecRoleDTO dto) {
        normalize(dto);
        validate(dto);
        ensureRoleCodeAvailable(dto.getRoleCode(), null);
        return super.add(dto);
    }

    @Override
    public SecRoleDTO update(Long id, SecRoleDTO dto) {
        requireRole(id);
        normalize(dto);
        validate(dto);
        ensureRoleCodeAvailable(dto.getRoleCode(), id);
        return super.update(id, dto);
    }

    @Override
    public SecRoleDTO edit(Long id, SecRoleDTO dto) {
        requireRole(id);
        normalize(dto);
        if (dto != null && dto.getRoleCode() != null) {
            if (!StringUtils.hasText(dto.getRoleCode())) {
                throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
            }
            ensureRoleCodeAvailable(dto.getRoleCode(), id);
        }
        return super.edit(id, dto);
    }

    @Override
    public boolean delete(Long id) {
        requireRole(id);
        return super.delete(id);
    }

    private void normalize(SecRoleDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setRoleCode(trimToNull(dto.getRoleCode()));
        dto.setRoleName(trimToNull(dto.getRoleName()));
        dto.setStatus(trimToNull(dto.getStatus()));
    }

    private void validate(SecRoleDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRoleCode())
                || !StringUtils.hasText(dto.getRoleName()) || !StringUtils.hasText(dto.getStatus())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private void ensureRoleCodeAvailable(String roleCode, Long excludedId) {
        SecRoleManagementEntity existing = baseMapper.selectByRoleCode(roleCode);
        if (existing != null && !existing.getId().equals(excludedId)) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
    }

    private void requireRole(Long id) {
        if (getById(id) == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
