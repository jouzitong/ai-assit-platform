package ai.platform.aiassit.user.api.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class UserPermissionQueryResponse {

    private Long userId;

    private String account;

    private String appCode;

    private Set<String> roleCodes = new HashSet<>();

    private Set<String> permissionCodes = new HashSet<>();
}
