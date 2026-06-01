package ai.platform.aiassit.user.api.dto;

import lombok.Data;

@Data
public class UserPermissionQueryRequest {

    private Long userId;

    private String account;

    private String appCode;
}
