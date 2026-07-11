package ai.platform.aiassit.user.security.management.entity.dto;

import lombok.Data;
import org.athena.framework.data.jdbc.entity.dto.IDTO;

@Data
public class SecRoleDTO implements IDTO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String status;
}
