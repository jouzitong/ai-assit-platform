package ai.platform.aiassit.user.api.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class UserQueryResponse {

    private Long userId;

    private String account;

    private String userName;

    private Integer status;

    private Set<String> roleCodes = new HashSet<>();

    private Map<String, Object> ext;
}
