package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.skill.SkillGatewayRequest;
import ai.platform.aiassit.agent.runtime.skill.SkillGatewayResponse;
import ai.platform.aiassit.agent.runtime.skill.SkillGatewayService;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Skill 受控资源读取网关。
 *
 * <p>在读取某个 Skill 版本的资源前确认当前登录用户，并将用户身份传入网关，以便按 Skill 定义执行资源访问控制。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/skill-gateway")
public class SkillGatewayController {

    private final SkillGatewayService gatewayService;

    public SkillGatewayController(SkillGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * 读取指定 Skill 版本声明的受控资源。
     *
     * @param skillCode Skill 业务编码
     * @param version   Skill 版本号
     * @param request   资源读取请求体，包含目标资源和运行时参数
     * @return 原始网关响应，包含资源内容或受控访问失败信息
     */
    @IgnoredResultWrapper
    @PostMapping("/{skillCode}/versions/{version}/resources/read")
    public SkillGatewayResponse read(@PathVariable String skillCode,
                                     @PathVariable Integer version,
                                     @RequestBody SkillGatewayRequest request) {
        UserContext current = SystemContext.getUserContext();
        if (current == null || current.subject() == null || current.subject().userId() == null) {
            throw BizException.of(ErrCodeConstant.LOGIN_FAILED);
        }
        return gatewayService.read(skillCode, version, request, current.subject().userId());
    }
}
