package ai.platform.aiassit.user.code.controller;

import ai.platform.aiassit.user.api.UserPermissionApi;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryRequest;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class UserPermissionController implements UserPermissionApi {

    @Override
    public UserPermissionQueryResponse queryPermissions(UserPermissionQueryRequest request) {
        UserPermissionQueryResponse response = new UserPermissionQueryResponse();
        if (request == null) {
            return response;
        }

        response.setUserId(request.getUserId());
        response.setAccount(request.getAccount());
        response.setAppCode(defaultAppCode(request.getAppCode()));
        response.setRoleCodes(Set.of("USER"));
        response.setPermissionCodes(Set.of("user:read"));
        return response;
    }

    private String defaultAppCode(String appCode) {
        if (StringUtils.hasText(appCode)) {
            return appCode;
        }
        return "app-platform-user";
    }
}
