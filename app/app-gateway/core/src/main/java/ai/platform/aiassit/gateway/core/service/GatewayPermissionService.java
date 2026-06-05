package ai.platform.aiassit.gateway.core.service;

import ai.platform.aiassit.gateway.core.context.GatewayPermissionSnapshot;
import ai.platform.aiassit.user.api.UserPermissionApi;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryRequest;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class GatewayPermissionService {

    private final UserPermissionApi userPermissionApi;

    public GatewayPermissionService(UserPermissionApi userPermissionApi) {
        this.userPermissionApi = userPermissionApi;
    }

    public GatewayPermissionSnapshot queryPermission(UserContext userContext, String appCode, Set<String> requiredPermissions) {
        UserPermissionQueryResponse response = userPermissionApi.queryPermissions(buildRequest(userContext, appCode));
        boolean granted = isGranted(response, requiredPermissions);
        return new GatewayPermissionSnapshot(response, granted);
    }

    public Set<String> parseRequiredPermissions(String requiredPermissionsHeader) {
        if (!StringUtils.hasText(requiredPermissionsHeader)) {
            return Set.of();
        }
        LinkedHashSet<String> requiredPermissions = new LinkedHashSet<>();
        Arrays.stream(requiredPermissionsHeader.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .forEach(requiredPermissions::add);
        return requiredPermissions;
    }

    public boolean isGranted(UserPermissionQueryResponse response, Set<String> requiredPermissions) {
        if (CollectionUtils.isEmpty(requiredPermissions)) {
            return true;
        }
        if (response == null || CollectionUtils.isEmpty(response.getPermissionCodes())) {
            return false;
        }
        return response.getPermissionCodes().containsAll(requiredPermissions);
    }

    private UserPermissionQueryRequest buildRequest(UserContext userContext, String appCode) {
        UserPermissionQueryRequest request = new UserPermissionQueryRequest();
        if (userContext != null && userContext.subject() != null) {
            request.setUserId(userContext.subject().userId());
            request.setAccount(userContext.subject().username());
        }
        request.setAppCode(StringUtils.hasText(appCode) ? appCode : "app-platform-user");
        return request;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
