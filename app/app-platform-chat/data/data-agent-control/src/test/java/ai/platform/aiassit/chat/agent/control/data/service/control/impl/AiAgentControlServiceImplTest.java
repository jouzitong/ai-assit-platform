package ai.platform.aiassit.chat.agent.control.data.service.control.impl;

import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentEntryBindingEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentVersionEntity;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.enums.AgentRuntimeType;
import ai.platform.aiassit.chat.agent.control.data.enums.DefinitionStatus;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiAgentEntryBindingMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiAgentMapper;
import ai.platform.aiassit.chat.agent.control.data.mapper.AiAgentVersionMapper;
import ai.platform.aiassit.chat.agent.control.data.service.control.AgentEntryEligibilityPolicy;
import ai.platform.aiassit.chat.agent.control.data.support.ControlPlaneJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentControlServiceImplTest {

    private final ControlPlaneJsonSupport json = new ControlPlaneJsonSupport(new ObjectMapper());
    private final AgentEntryEligibilityPolicy eligibility = new AgentEntryEligibilityPolicy(json);

    @Test
    void resolveEntrySkipsDirtySpecialistBindingAndUsesEligibleRoot() {
        ArrayDeque<AiAgentEntity> agents = new ArrayDeque<>(List.of(
                agent("result-reviewer"), agent("home-assistant")));
        ArrayDeque<AiAgentVersionEntity> versions = new ArrayDeque<>(List.of(
                published("result-reviewer", null), published("home-assistant", "HOME_CHAT")));
        AiAgentEntryBindingMapper bindings = stub(AiAgentEntryBindingMapper.class, (method, args) ->
                "selectList".equals(method) ? List.of(
                        binding("result-reviewer", 0), binding("home-assistant", 10)) : null);
        AiAgentControlServiceImpl service = service(
                stub(AiAgentMapper.class, (method, args) ->
                        "selectOne".equals(method) ? agents.removeFirst() : null),
                stub(AiAgentVersionMapper.class, (method, args) ->
                        "selectOne".equals(method) ? versions.removeFirst() : null),
                bindings);

        var resolved = service.resolveEntry("HOME_CHAT");

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().getAgentCode()).isEqualTo("home-assistant");
    }

    @Test
    void listAvailableFindsEligiblePublishedRootsWithoutDependingOnBindings() {
        ArrayDeque<AiAgentVersionEntity> versions = new ArrayDeque<>(List.of(
                published("result-reviewer", null), published("home-assistant", "HOME_CHAT")));
        AtomicInteger bindingCalls = new AtomicInteger();
        AiAgentControlServiceImpl service = service(
                stub(AiAgentMapper.class, (method, args) -> "selectList".equals(method)
                        ? List.of(agent("result-reviewer"), agent("home-assistant")) : null),
                stub(AiAgentVersionMapper.class, (method, args) ->
                        "selectOne".equals(method) ? versions.removeFirst() : null),
                stub(AiAgentEntryBindingMapper.class, (method, args) -> {
                    bindingCalls.incrementAndGet();
                    return null;
                }));

        var available = service.listAvailable("HOME_CHAT");

        assertThat(available)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getCode()).isEqualTo("home-assistant");
                    assertThat(candidate.getVersion()).isEqualTo(1);
                });
        assertThat(bindingCalls).hasValue(0);
    }

    @Test
    void updateEntrySelectionRejectsPublishedSpecialistWithoutEntryLabel() {
        AtomicInteger bindingCalls = new AtomicInteger();
        AiAgentControlServiceImpl service = service(
                stub(AiAgentMapper.class, (method, args) ->
                        "selectOne".equals(method) ? agent("result-reviewer") : null),
                stub(AiAgentVersionMapper.class, (method, args) ->
                        "selectOne".equals(method) ? published("result-reviewer", null) : null),
                stub(AiAgentEntryBindingMapper.class, (method, args) -> {
                    bindingCalls.incrementAndGet();
                    return null;
                }));
        AgentControlDTOs.EntrySelectionRequest request = new AgentControlDTOs.EntrySelectionRequest();
        request.setAgentCode("result-reviewer");
        request.setVersionStrategy("LATEST_PUBLISHED");

        assertThatThrownBy(() -> service.updateEntrySelection("HOME_CHAT", request))
                .isInstanceOf(BizException.class);
        assertThat(bindingCalls).hasValue(0);
    }

    @Test
    void upsertEntryBindingRejectsPublishedSpecialistWithoutEntryLabel() {
        AtomicInteger bindingCalls = new AtomicInteger();
        AiAgentControlServiceImpl service = service(
                stub(AiAgentMapper.class, (method, args) -> null),
                stub(AiAgentVersionMapper.class, (method, args) ->
                        "selectOne".equals(method) ? published("result-reviewer", null) : null),
                stub(AiAgentEntryBindingMapper.class, (method, args) -> {
                    bindingCalls.incrementAndGet();
                    return null;
                }));
        AgentControlDTOs.EntryBindingRequest request = new AgentControlDTOs.EntryBindingRequest();
        request.setAgentCode("result-reviewer");
        request.setAgentVersion(1);
        request.setRuntimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON);

        assertThatThrownBy(() -> service.upsertEntryBinding("HOME_CHAT", request))
                .isInstanceOf(BizException.class);
        assertThat(bindingCalls).hasValue(0);
    }

    private AiAgentControlServiceImpl service(AiAgentMapper agentMapper,
                                              AiAgentVersionMapper versionMapper,
                                              AiAgentEntryBindingMapper bindingMapper) {
        return new AiAgentControlServiceImpl(
                agentMapper, versionMapper, bindingMapper,
                null, null, null, null, null, null,
                eligibility, json, null);
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.apply(method.getName(), args));
    }

    private AiAgentEntity agent(String code) {
        AiAgentEntity agent = new AiAgentEntity();
        agent.setCode(code);
        agent.setName(code);
        agent.setCurrentVersion(1);
        agent.setStatus(DefinitionStatus.PUBLISHED);
        agent.setEnabled(Boolean.TRUE);
        return agent;
    }

    private AiAgentEntryBindingEntity binding(String agentCode, int priority) {
        AiAgentEntryBindingEntity binding = new AiAgentEntryBindingEntity();
        binding.setEntryCode("HOME_CHAT");
        binding.setAgentCode(agentCode);
        binding.setAgentVersion(1);
        binding.setRuntimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON);
        binding.setPriority(priority);
        binding.setEnabled(Boolean.TRUE);
        return binding;
    }

    private AiAgentVersionEntity published(String agentCode, String entry) {
        AgentControlDTOs.Manifest manifest = new AgentControlDTOs.Manifest();
        manifest.getMetadata().setCode(agentCode);
        manifest.getMetadata().setVersion(1);
        manifest.getMetadata().setName(agentCode);
        if (entry != null) {
            manifest.getMetadata().setLabels(Map.of("entry", entry));
        }
        manifest.getSpec().getInstructions().setText("Execute " + agentCode);
        AgentControlDTOs.ModelRef model = new AgentControlDTOs.ModelRef();
        model.setRef("model://default-quality");
        manifest.getSpec().setModel(model);

        AiAgentVersionEntity version = new AiAgentVersionEntity();
        version.setAgentCode(agentCode);
        version.setVersionNo(1);
        version.setStatus(DefinitionStatus.PUBLISHED);
        version.setManifestJson(json.write(manifest));
        version.setChecksum("checksum-" + agentCode);
        return version;
    }
}
