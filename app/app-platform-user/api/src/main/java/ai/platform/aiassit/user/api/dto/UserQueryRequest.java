package ai.platform.aiassit.user.api.dto;

import lombok.Data;

@Data
public class UserQueryRequest {

    private Long userId;

    private String account;

    private String email;

    private String phone;
}
