package ai.platform.aiassit.user.code.controller;

import ai.platform.aiassit.user.api.UserQueryApi;
import ai.platform.aiassit.user.api.dto.UserQueryRequest;
import ai.platform.aiassit.user.api.dto.UserQueryResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Set;

@RestController
public class UserQueryController implements UserQueryApi {

    @Override
    public UserQueryResponse queryUser(UserQueryRequest request) {
        UserQueryResponse response = new UserQueryResponse();
        if (request == null) {
            return response;
        }

        response.setUserId(request.getUserId());
        response.setAccount(firstNonBlank(request.getAccount(), request.getEmail(), request.getPhone()));
        response.setUserName("UNKNOWN");
        response.setStatus(1);
        response.setRoleCodes(Set.of("USER"));

        LinkedHashMap<String, Object> ext = new LinkedHashMap<>();
        ext.put("email", request.getEmail());
        ext.put("phone", request.getPhone());
        response.setExt(ext);
        return response;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
