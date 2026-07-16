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

@RestController
@RequestMapping("/api/v1/ai/skill-gateway")
public class SkillGatewayController {

    private final SkillGatewayService gatewayService;

    public SkillGatewayController(SkillGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

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
