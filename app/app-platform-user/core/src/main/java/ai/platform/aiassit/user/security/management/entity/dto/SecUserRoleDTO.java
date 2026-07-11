package ai.platform.aiassit.user.security.management.entity.dto;

import lombok.Data;
import org.athena.framework.data.jdbc.entity.dto.IDTO;

/**
 * 用户角色关联传输对象。
 */
@Data
public class SecUserRoleDTO implements IDTO {

    private Long id;

    private Long userId;

    private String roleCode;
}
