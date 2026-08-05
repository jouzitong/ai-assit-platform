package ai.platform.aiassit.user.code.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.auth.AuthenticationRequest;
import org.athena.framework.security.api.auth.AuthenticationResult;
import org.athena.framework.security.api.model.AuthorizationSnapshot;
import org.athena.framework.security.api.model.SessionState;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.auth.core.extractor.CredentialExtractor;
import org.athena.framework.security.auth.core.service.SecurityAuthenticationFacade;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Athena 认证链路的 OpenWebUI 兼容认证接口。
 *
 * <p>将平台登录态、角色和权限快照投影为 OpenWebUI 所需的 Token 与用户结构；认证、令牌提取和登出失效仍由底层安全门面统一负责。</p>
 */
@RestController
@RequestMapping("/api/v1/auths")
public class OpenWebUiAuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final SecurityAuthenticationFacade authenticationFacade;
    private final CredentialExtractor credentialExtractor;

    public OpenWebUiAuthController(SecurityAuthenticationFacade authenticationFacade,
                                   CredentialExtractor credentialExtractor) {
        this.authenticationFacade = authenticationFacade;
        this.credentialExtractor = credentialExtractor;
    }

    /**
     * 使用邮箱和密码登录，并返回 OpenWebUI 兼容的会话用户信息。
     *
     * @param command 登录请求体，包含邮箱与密码
     * @param request 原始 HTTP 请求，用于记录客户端地址
     * @return 未包装的会话响应，包含访问令牌、过期时间、用户展示信息、角色和权限快照
     */
    @IgnoredResultWrapper
    @PostMapping("/signin")
    public SessionUserResponse signin(@RequestBody SigninForm command, HttpServletRequest request) {
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
            command == null ? null : command.getEmail(),
            command == null ? null : command.getPassword(),
            null,
            "PASSWORD",
            request.getRemoteAddr()
        );
        AuthenticationResult result = authenticationFacade.authenticate(authenticationRequest);
        if (!result.success() || result.context() == null || result.context().session() == null) {
            throw BizException.ofStatus(ErrCodeConstant.LOGIN_FAILED, HttpStatus.UNAUTHORIZED.value());
        }
        return toSessionUserResponse(result.context(), result.context().session().tokenId(), false);
    }

    /**
     * 查询当前访问令牌对应的 OpenWebUI 用户资料。
     *
     * @param request 原始 HTTP 请求，用于提取当前访问令牌
     * @return 未包装的用户资料，包含会话信息、角色权限和 OpenWebUI 扩展资料字段
     */
    @IgnoredResultWrapper
    @GetMapping({"", "/"})
    public SessionUserInfoResponse getSessionUser(HttpServletRequest request) {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext == null || userContext.subject() == null) {
            throw BizException.ofStatus(ErrCodeConstant.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
        }
        return toSessionUserResponse(userContext, credentialExtractor.extractToken(request), true);
    }

    /**
     * 注销当前访问令牌对应的会话。
     *
     * @param request 原始 HTTP 请求，用于提取并失效访问令牌
     * @return 未包装的状态对象；{@code status=true} 表示注销处理已完成
     */
    @IgnoredResultWrapper
    @PostMapping("/signout")
    public Map<String, Object> signout(HttpServletRequest request) {
        String token = credentialExtractor.extractToken(request);
        if (StringUtils.isNotBlank(token)) {
            authenticationFacade.logout(token);
        }
        return Map.of("status", Boolean.TRUE);
    }

    private SessionUserInfoResponse toSessionUserResponse(UserContext userContext, String token, boolean includeProfileFields) {
        SessionUserInfoResponse response = new SessionUserInfoResponse();
        response.setToken(token);
        response.setTokenType(TOKEN_TYPE);
        response.setExpiresAt(toEpochSecond(userContext.session()));

        Long userId = userContext.subject().userId();
        String username = userContext.subject().username();
        String displayName = userContext.attributes() == null ? null : stringify(userContext.attributes().get("displayName"));
        Set<String> roles = readRoles(userContext.authorization());
        Set<String> permissionCodes = readPermissions(userContext.authorization());

        response.setId(userId == null ? null : String.valueOf(userId));
        response.setEmail(resolveEmail(username));
        response.setName(StringUtils.defaultIfBlank(displayName, username));
        response.setRole(resolveOpenWebUiRole(roles));
        response.setProfileImageUrl(null);
        response.setPermissions(buildPermissions(roles, permissionCodes));

        if (includeProfileFields) {
            response.setBio(null);
            response.setGender(null);
            response.setDateOfBirth(null);
            response.setStatusEmoji(null);
            response.setStatusMessage(null);
            response.setStatusExpiresAt(null);
        }
        return response;
    }

    private Long toEpochSecond(SessionState session) {
        Instant expireAt = session == null ? null : session.expireAt();
        return expireAt == null ? null : expireAt.getEpochSecond();
    }

    private String resolveEmail(String username) {
        if (!StringUtils.contains(username, "@")) {
            return null;
        }
        return username;
    }

    private String resolveOpenWebUiRole(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "pending";
        }
        if (containsIgnoreCase(roles, "admin")) {
            return "admin";
        }
        if (containsIgnoreCase(roles, "user")) {
            return "user";
        }
        if (containsIgnoreCase(roles, "pending")) {
            return "pending";
        }
        return roles.iterator().next().toLowerCase();
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        for (String value : values) {
            if (StringUtils.equalsIgnoreCase(value, target)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> readRoles(AuthorizationSnapshot authorization) {
        if (authorization == null || authorization.roles() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(authorization.roles());
    }

    private Set<String> readPermissions(AuthorizationSnapshot authorization) {
        if (authorization == null || authorization.permissions() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(authorization.permissions());
    }

    private Map<String, Object> buildPermissions(Set<String> roles, Set<String> permissionCodes) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("role_codes", roles == null ? Set.of() : roles);
        permissions.put("permission_codes", permissionCodes == null ? Set.of() : permissionCodes);
        return permissions;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * OpenWebUI 兼容登录请求体。
     *
     * <p>{@code email} 作为平台认证账号传入，{@code password} 为待验证的登录密码。</p>
     */
    @Data
    public static class SigninForm {

        private String email;

        private String password;
    }

    /**
     * OpenWebUI 兼容的基础会话响应体。
     *
     * <p>包含认证令牌、令牌类型、过期时间及由平台角色和权限快照转换后的用户信息。</p>
     */
    @Data
    public static class SessionUserResponse {

        private String token;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_at")
        private Long expiresAt;

        private String id;

        private String email;

        private String name;

        private String role;

        @JsonProperty("profile_image_url")
        private String profileImageUrl;

        private Map<String, Object> permissions;
    }

    /**
     * OpenWebUI 兼容的当前用户资料响应体。
     *
     * <p>在基础会话响应上补齐 OpenWebUI 资料字段；当前未由平台维护的资料字段以空值返回以保持协议兼容。</p>
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SessionUserInfoResponse extends SessionUserResponse {

        private String bio;

        private String gender;

        @JsonProperty("date_of_birth")
        private Object dateOfBirth;

        @JsonProperty("status_emoji")
        private String statusEmoji;

        @JsonProperty("status_message")
        private String statusMessage;

        @JsonProperty("status_expires_at")
        private Long statusExpiresAt;
    }
}
