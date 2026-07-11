package ai.platform.aiassit.user.security.management.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户档案更新请求；password 仅用于写入，绝不回显。
 */
@Data
public class SecUserProfileUpdateRequest {

    private SecUserDTO user;

    private List<String> roleCodes;

    private String password;
}
