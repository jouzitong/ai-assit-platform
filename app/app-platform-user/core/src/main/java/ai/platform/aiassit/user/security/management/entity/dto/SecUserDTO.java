package ai.platform.aiassit.user.security.management.entity.dto;

import lombok.Data;
import org.athena.framework.data.jdbc.entity.dto.IDTO;

/**
 * 用户管理传输对象。
 */
@Data
public class SecUserDTO implements IDTO {

    private Long id;

    private String username;

    private String displayName;

    private String status;

    private String tenantId;
}
