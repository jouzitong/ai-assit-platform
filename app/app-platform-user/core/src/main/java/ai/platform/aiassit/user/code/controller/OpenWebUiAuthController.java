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
 * Exposes an OpenWebUI-compatible auth payload on top of Athena's auth pipeline.
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
            throw BizException.of(ErrCodeConstant.LOGIN_FAILED, HttpStatus.UNAUTHORIZED.value());
        }
        return toSessionUserResponse(result.context(), result.context().session().tokenId(), false);
    }

    @IgnoredResultWrapper
    @GetMapping({"", "/"})
    public SessionUserInfoResponse getSessionUser(HttpServletRequest request) {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext == null || userContext.subject() == null) {
            throw BizException.of(ErrCodeConstant.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
        }
        return toSessionUserResponse(userContext, credentialExtractor.extractToken(request), true);
    }

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

    @Data
    public static class SigninForm {

        private String email;

        private String password;
    }

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
