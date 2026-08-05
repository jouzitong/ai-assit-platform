package ai.platform.aiassit.user.code.controller;

import ai.platform.aiassit.user.api.UserQueryApi;
import ai.platform.aiassit.user.api.dto.UserQueryRequest;
import ai.platform.aiassit.user.api.dto.UserQueryResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 用户基础信息的内部兼容查询接口。
 *
 * <p>当前实现根据请求中的账号、邮箱或手机号构造平台间可传递的最小用户快照，并返回默认状态和基础角色，供调用链在用户域完整接入前保持协议兼容。</p>
 */
@RestController
public class UserQueryController implements UserQueryApi {

    /**
     * 查询或构造用户基础信息快照。
     *
     * @param request 用户查询请求体，可提供用户标识、账号、邮箱或手机号
     * @return 用户基础信息响应，包含优先解析的账号、默认状态、基础角色及原始联系方式扩展字段
     */
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
