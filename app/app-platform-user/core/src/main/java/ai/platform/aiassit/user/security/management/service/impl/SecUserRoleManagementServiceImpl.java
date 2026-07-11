package ai.platform.aiassit.user.security.management.service.impl;

import ai.platform.aiassit.user.security.management.convert.SecUserRoleManagementConvert;
import ai.platform.aiassit.user.security.management.entity.SecUserManagementEntity;
import ai.platform.aiassit.user.security.management.entity.SecUserRoleManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecUserRoleQueryRequest;
import ai.platform.aiassit.user.security.management.mapper.SecUserManagementMapper;
import ai.platform.aiassit.user.security.management.mapper.SecUserRoleManagementMapper;
import ai.platform.aiassit.user.security.management.mapper.SecRoleManagementMapper;
import ai.platform.aiassit.user.security.management.service.SecUserRoleManagementService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SecUserRoleManagementServiceImpl
        extends BaseMapperService<SecUserRoleManagementEntity, SecUserRoleManagementMapper, SecUserRoleDTO>
        implements SecUserRoleManagementService {

    private final SecUserRoleManagementConvert convert;
    private final SecUserManagementMapper userMapper;
    private final SecRoleManagementMapper roleMapper;

    public SecUserRoleManagementServiceImpl(SecUserRoleManagementConvert convert,
                                            SecUserManagementMapper userMapper,
                                            SecRoleManagementMapper roleMapper) {
        this.convert = convert;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    protected IConvert<SecUserRoleManagementEntity, SecUserRoleDTO> convert() {
        return convert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<SecUserRoleManagementEntity> buildQuery(Query query) {
        QueryWrapper<SecUserRoleManagementEntity> wrapper = super.buildQuery(query);
        if (query instanceof SecUserRoleQueryRequest request && StringUtils.hasText(request.getKeyword())) {
            wrapper.like("role_code", request.getKeyword().trim());
        }
        return wrapper.orderByDesc("id");
    }

    @Override
    public SecUserRoleDTO add(SecUserRoleDTO dto) {
        normalize(dto);
        validateForCreateOrUpdate(dto);
        ensureUserExists(dto.getUserId());
        ensureRoleExists(dto.getRoleCode());
        ensureUserRoleAvailable(dto.getUserId(), dto.getRoleCode(), null);
        return super.add(dto);
    }

    @Override
    public SecUserRoleDTO update(Long id, SecUserRoleDTO dto) {
        requireUserRole(id);
        normalize(dto);
        validateForCreateOrUpdate(dto);
        ensureUserExists(dto.getUserId());
        ensureRoleExists(dto.getRoleCode());
        ensureUserRoleAvailable(dto.getUserId(), dto.getRoleCode(), id);
        return super.update(id, dto);
    }

    @Override
    public SecUserRoleDTO edit(Long id, SecUserRoleDTO dto) {
        SecUserRoleManagementEntity existing = requireUserRole(id);
        normalize(dto);
        Long userId = dto != null && dto.getUserId() != null ? dto.getUserId() : existing.getUserId();
        String roleCode = dto != null && dto.getRoleCode() != null ? dto.getRoleCode() : existing.getRoleCode();
        if (userId == null || !StringUtils.hasText(roleCode)) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
        ensureUserExists(userId);
        ensureRoleExists(roleCode);
        ensureUserRoleAvailable(userId, roleCode, id);
        return super.edit(id, dto);
    }

    @Override
    public boolean delete(Long id) {
        requireUserRole(id);
        return super.delete(id);
    }

    private void normalize(SecUserRoleDTO dto) {
        if (dto != null) {
            dto.setRoleCode(StringUtils.hasText(dto.getRoleCode()) ? dto.getRoleCode().trim() : null);
        }
    }

    private void validateForCreateOrUpdate(SecUserRoleDTO dto) {
        if (dto == null || dto.getUserId() == null || !StringUtils.hasText(dto.getRoleCode())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private void ensureUserExists(Long userId) {
        SecUserManagementEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
    }

    private void ensureUserRoleAvailable(Long userId, String roleCode, Long excludedId) {
        SecUserRoleManagementEntity existing = baseMapper.selectByUserIdAndRoleCode(userId, roleCode);
        if (existing != null && !existing.getId().equals(excludedId)) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
    }

    private void ensureRoleExists(String roleCode) {
        if (roleMapper.selectByRoleCode(roleCode) == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
    }

    private SecUserRoleManagementEntity requireUserRole(Long id) {
        SecUserRoleManagementEntity userRole = getById(id);
        if (userRole == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return userRole;
    }
}
