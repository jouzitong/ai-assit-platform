package ai.platform.aiassit.user.security.management.service.impl;

import ai.platform.aiassit.user.security.management.convert.SecUserManagementConvert;
import ai.platform.aiassit.user.security.management.entity.SecUserManagementEntity;
import ai.platform.aiassit.user.security.management.entity.SecUserCredentialManagementEntity;
import ai.platform.aiassit.user.security.management.entity.SecUserRoleManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileUpdateRequest;
import ai.platform.aiassit.user.security.management.entity.req.SecUserQueryRequest;
import ai.platform.aiassit.user.security.management.mapper.SecRoleManagementMapper;
import ai.platform.aiassit.user.security.management.mapper.SecUserCredentialManagementMapper;
import ai.platform.aiassit.user.security.management.mapper.SecUserManagementMapper;
import ai.platform.aiassit.user.security.management.mapper.SecUserRoleManagementMapper;
import ai.platform.aiassit.user.security.management.service.SecUserManagementService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class SecUserManagementServiceImpl
        extends BaseMapperService<SecUserManagementEntity, SecUserManagementMapper, SecUserDTO>
        implements SecUserManagementService {

    private final SecUserManagementConvert convert;
    private final SecUserRoleManagementMapper userRoleMapper;
    private final SecRoleManagementMapper roleMapper;
    private final SecUserCredentialManagementMapper credentialMapper;
    private final PasswordEncoder passwordEncoder;

    public SecUserManagementServiceImpl(SecUserManagementConvert convert,
                                        SecUserRoleManagementMapper userRoleMapper,
                                        SecRoleManagementMapper roleMapper,
                                        SecUserCredentialManagementMapper credentialMapper,
                                        PasswordEncoder passwordEncoder) {
        this.convert = convert;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.credentialMapper = credentialMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected IConvert<SecUserManagementEntity, SecUserDTO> convert() {
        return convert;
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<SecUserManagementEntity> buildQuery(Query query) {
        QueryWrapper<SecUserManagementEntity> wrapper = super.buildQuery(query);
        if (query instanceof SecUserQueryRequest request && StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(item -> item.like("username", keyword)
                    .or()
                    .like("display_name", keyword)
                    .or()
                    .like("tenant_id", keyword));
        }
        return wrapper.orderByDesc("id");
    }

    @Override
    public SecUserDTO add(SecUserDTO dto) {
        normalize(dto);
        validateForCreateOrUpdate(dto);
        ensureUsernameAvailable(dto.getUsername(), null);
        return super.add(dto);
    }

    @Override
    public SecUserDTO update(Long id, SecUserDTO dto) {
        requireUser(id);
        normalize(dto);
        validateForCreateOrUpdate(dto);
        ensureUsernameAvailable(dto.getUsername(), id);
        return super.update(id, dto);
    }

    @Override
    public SecUserDTO edit(Long id, SecUserDTO dto) {
        requireUser(id);
        normalize(dto);
        if (dto != null && dto.getUsername() != null) {
            if (!StringUtils.hasText(dto.getUsername())) {
                throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
            }
            ensureUsernameAvailable(dto.getUsername(), id);
        }
        return super.edit(id, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        requireUser(id);
        userRoleMapper.deleteByUserId(id);
        return super.delete(id);
    }

    @Override
    public SecUserProfileDTO getProfile(Long userId) {
        SecUserManagementEntity user = requireUser(userId);
        return toProfile(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecUserProfileDTO updateProfile(Long userId, SecUserProfileUpdateRequest request) {
        SecUserManagementEntity user = requireUser(userId);
        SecUserDTO userDTO = request == null ? null : request.getUser();
        normalize(userDTO);
        validateForCreateOrUpdate(userDTO);
        ensureUsernameAvailable(userDTO.getUsername(), userId);
        convert.updateEntityFromDto(userDTO, user);
        updateById(user);

        syncUserRoles(userId, request.getRoleCodes());
        updatePassword(userId, request.getPassword());
        return toProfile(user);
    }

    private void normalize(SecUserDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setUsername(trimToNull(dto.getUsername()));
        dto.setDisplayName(trimToNull(dto.getDisplayName()));
        dto.setStatus(trimToNull(dto.getStatus()));
        dto.setTenantId(trimToNull(dto.getTenantId()));
    }

    private void validateForCreateOrUpdate(SecUserDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getStatus())) {
            throw BizException.of(ErrCodeConstant.ILLEGAL_PARAMETER_ERROR);
        }
    }

    private void ensureUsernameAvailable(String username, Long excludedId) {
        SecUserManagementEntity existing = baseMapper.selectByUsername(username);
        if (existing != null && !existing.getId().equals(excludedId)) {
            throw BizException.of(ErrCodeConstant.DUPLICATE_REQUEST);
        }
    }

    private SecUserManagementEntity requireUser(Long id) {
        SecUserManagementEntity user = getById(id);
        if (user == null) {
            throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private SecUserProfileDTO toProfile(SecUserManagementEntity user) {
        SecUserProfileDTO profile = new SecUserProfileDTO();
        profile.setUser(convert.toDTO(user));
        profile.setRoleCodes(userRoleMapper.selectByUserId(user.getId()).stream()
                .map(SecUserRoleManagementEntity::getRoleCode)
                .toList());
        SecUserCredentialManagementEntity credential = credentialMapper.selectPasswordByUserId(user.getId());
        profile.setPasswordConfigured(credential != null && StringUtils.hasText(credential.getPasswordHash()));
        profile.setPasswordAlgo(credential == null ? null : credential.getPasswordAlgo());
        return profile;
    }

    private void syncUserRoles(Long userId, List<String> roleCodes) {
        Set<String> normalizedCodes = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String roleCode : roleCodes) {
                if (StringUtils.hasText(roleCode)) {
                    normalizedCodes.add(roleCode.trim());
                }
            }
        }
        for (String roleCode : normalizedCodes) {
            if (roleMapper.selectByRoleCode(roleCode) == null) {
                throw BizException.of(ErrCodeConstant.RESOURCE_NOT_FOUND);
            }
        }
        userRoleMapper.deleteByUserId(userId);
        for (String roleCode : normalizedCodes) {
            SecUserRoleManagementEntity userRole = new SecUserRoleManagementEntity();
            userRole.setUserId(userId);
            userRole.setRoleCode(roleCode);
            userRoleMapper.insert(userRole);
        }
    }

    private void updatePassword(Long userId, String password) {
        if (!StringUtils.hasText(password)) {
            return;
        }
        SecUserCredentialManagementEntity credential = credentialMapper.selectPasswordByUserId(userId);
        if (credential == null) {
            credential = new SecUserCredentialManagementEntity();
            credential.setUserId(userId);
            credential.setCredentialType("PASSWORD");
            credential.setPasswordSalt(null);
            credential.setPasswordAlgo("BCRYPT");
            credential.setPasswordHash(passwordEncoder.encode(password));
            credentialMapper.insert(credential);
            return;
        }
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setPasswordAlgo("BCRYPT");
        credential.setPasswordSalt(null);
        credentialMapper.updateById(credential);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
