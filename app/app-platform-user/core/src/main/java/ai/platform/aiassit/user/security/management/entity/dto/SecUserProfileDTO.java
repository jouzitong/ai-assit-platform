package ai.platform.aiassit.user.security.management.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户编辑所需的聚合信息。密码散列不对外返回。
 */
@Data
public class SecUserProfileDTO {

    private SecUserDTO user;

    private List<String> roleCodes;

    private Boolean passwordConfigured;

    private String passwordAlgo;
}
