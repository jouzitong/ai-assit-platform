package ai.platform.aiassit.agent.runtime.skill;

import ai.platform.aiassit.agent.runtime.AgentCapabilityGrantService;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResource;
import ai.platform.aiassit.service.ai.spi.skill.PublishedSkillResourceStore;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillGatewayServiceTest {

    @Test
    void readsOnlyResourceGrantedToTheActiveAgentRun() {
        PublishedSkillResourceStore store = (code, version, path) -> Optional.of(
                PublishedSkillResource.builder()
                        .skillCode(code).skillVersion(version).path(path)
                        .mediaType("text/markdown").checksum("hash")
                        .content("# Instructions".getBytes(StandardCharsets.UTF_8)).build());
        AgentCapabilityGrantService grants = new AgentCapabilityGrantService();
        AgentDefinitionSnapshot snapshot = new AgentDefinitionSnapshot();
        snapshot.setSnapshotHash("sha256:test");
        snapshot.setResolvedCapabilities(Map.of("skills", List.of(
                Map.of("code", "analysis", "version", 2))));
        grants.register("run-1", 7L, snapshot, Duration.ofMinutes(1));
        SkillGatewayService service = new SkillGatewayService(store, grants);
        SkillGatewayRequest request = new SkillGatewayRequest();
        request.setPath("SKILL.md");
        request.setRun(Map.of("runId", "run-1", "snapshotHash", "sha256:test"));

        SkillGatewayResponse response = service.read("analysis", 2, request, 7L);

        assertThat(response.getEncoding()).isEqualTo("utf-8");
        assertThat(response.getContent()).isEqualTo("# Instructions");
        assertThatThrownBy(() -> service.read("other", 1, request, 7L))
                .isInstanceOf(BizException.class);
    }
}
