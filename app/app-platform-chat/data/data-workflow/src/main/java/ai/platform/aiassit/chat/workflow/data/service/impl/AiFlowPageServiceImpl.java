package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowEntity;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowCatalogConfig;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeConfigItem;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeRuntimeConfig;
import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowRuntimeConfig;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowCardDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowDetailDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowNodeDetailDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowNodeSkillItemDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowOverviewDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowWorkflowFormDTO;
import ai.platform.aiassit.chat.workflow.data.service.AiFlowPageService;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatNodeMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigNodeMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigNodeSkillMapper;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiFlowPageServiceImpl implements AiFlowPageService {

    private final AiChatWorkflowMapper workflowMapper;
    private final AiChatNodeMapper nodeMapper;
    private final AiChatSkillMapper skillMapper;
    private final AiChatWorkflowConfigMapper workflowConfigMapper;
    private final AiChatWorkflowConfigNodeMapper configNodeMapper;
    private final AiChatWorkflowConfigNodeSkillMapper configNodeSkillMapper;

    public AiFlowPageServiceImpl(AiChatWorkflowMapper workflowMapper,
                                 AiChatNodeMapper nodeMapper,
                                 AiChatSkillMapper skillMapper,
                                 AiChatWorkflowConfigMapper workflowConfigMapper,
                                 AiChatWorkflowConfigNodeMapper configNodeMapper,
                                 AiChatWorkflowConfigNodeSkillMapper configNodeSkillMapper) {
        this.workflowMapper = workflowMapper;
        this.nodeMapper = nodeMapper;
        this.skillMapper = skillMapper;
        this.workflowConfigMapper = workflowConfigMapper;
        this.configNodeMapper = configNodeMapper;
        this.configNodeSkillMapper = configNodeSkillMapper;
    }

    @Override
    public AiFlowOverviewDTO overview() {
        List<AiChatWorkflowEntity> workflows = listAllWorkflows();
        List<AiChatNodeEntity> nodes = listAllNodes();
        List<AiChatSkillEntity> skills = listAllSkills();
        List<AiChatWorkflowConfigEntity> configs = listAllConfigs();
        List<AiChatWorkflowConfigNodeEntity> configNodes = listAllConfigNodes();
        List<AiChatWorkflowConfigNodeSkillEntity> configNodeSkills = listAllConfigNodeSkills();

        Map<String, AiChatWorkflowConfigEntity> configByWorkflowCode = configs.stream()
                .collect(Collectors.toMap(AiChatWorkflowConfigEntity::getWorkflowCode, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<AiChatWorkflowConfigNodeEntity>> configNodesByConfigCode = configNodes.stream()
                .collect(Collectors.groupingBy(AiChatWorkflowConfigNodeEntity::getConfigCode, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<AiChatWorkflowConfigNodeSkillEntity>> skillBindingsByNodeCode = configNodeSkills.stream()
                .collect(Collectors.groupingBy(AiChatWorkflowConfigNodeSkillEntity::getNodeCode, LinkedHashMap::new, Collectors.toList()));

        Map<String, AiChatWorkflowEntity> workflowByCode = workflows.stream()
                .collect(Collectors.toMap(AiChatWorkflowEntity::getCode, item -> item, (left, right) -> left, LinkedHashMap::new));

        AiFlowOverviewDTO overview = new AiFlowOverviewDTO();
        overview.setWorkflows(workflows.stream().map(workflow -> buildWorkflowCard(workflow, configByWorkflowCode.get(workflow.getCode()), configNodesByConfigCode, nodes)).toList());
        overview.setNodes(nodes.stream().map(node -> buildNodeCard(node, workflowByCode, configs, configNodes)).toList());
        overview.setSkills(skills.stream().map(skill -> buildSkillCard(skill, skillBindingsByNodeCode)).toList());
        return overview;
    }

    @Override
    public AiFlowDetailDTO detail(String workflowKey) {
        AiChatWorkflowEntity workflow = findWorkflowByRouteKey(workflowKey);
        if (workflow == null) {
            return null;
        }

        AiChatWorkflowConfigEntity config = findPrimaryConfig(workflow.getCode());
        AiFlowDetailDTO detail = new AiFlowDetailDTO();
        detail.setWorkflowId(workflow.getId());
        detail.setWorkflowKey(resolveRouteKey(workflow));
        detail.setWorkflowCode(workflow.getCode());
        detail.setWorkflowName(workflow.getName());
        detail.setWorkflowScene(workflow.getConfig() == null ? null : workflow.getConfig().getSceneDesc());
        detail.setWorkflowStatus(resolveStatus(workflow.getEnabled(), "已接入", "待补充"));
        detail.setWorkflowTags(workflow.getConfig() == null ? new ArrayList<>() : workflow.getConfig().getTags());

        if (config == null) {
            return detail;
        }

        detail.setConfigId(config.getId());
        detail.setConfigCode(config.getCode());

        List<AiChatWorkflowConfigNodeEntity> configNodes = configNodeMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigNodeEntity>()
                .eq(AiChatWorkflowConfigNodeEntity::getConfigCode, config.getCode())
                .orderByAsc(AiChatWorkflowConfigNodeEntity::getSort)
                .orderByAsc(AiChatWorkflowConfigNodeEntity::getId));
        List<AiChatWorkflowConfigNodeSkillEntity> skillBindings = configNodeSkillMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigNodeSkillEntity>()
                .eq(AiChatWorkflowConfigNodeSkillEntity::getConfigCode, config.getCode())
                .orderByAsc(AiChatWorkflowConfigNodeSkillEntity::getSort)
                .orderByAsc(AiChatWorkflowConfigNodeSkillEntity::getId));
        Map<String, AiChatNodeEntity> nodeByCode = listAllNodes().stream()
                .collect(Collectors.toMap(AiChatNodeEntity::getCode, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, AiChatSkillEntity> skillByCode = listAllSkills().stream()
                .collect(Collectors.toMap(AiChatSkillEntity::getCode, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<AiChatWorkflowConfigNodeSkillEntity>> bindingByNodeCode = skillBindings.stream()
                .collect(Collectors.groupingBy(AiChatWorkflowConfigNodeSkillEntity::getNodeCode, LinkedHashMap::new, Collectors.toList()));

        detail.setNodeDefinitions(configNodes.stream().map(item -> buildNodeDetail(item, nodeByCode.get(item.getNodeCode()), bindingByNodeCode.get(item.getNodeCode()), skillByCode)).toList());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiFlowDetailDTO saveWorkflow(AiFlowWorkflowFormDTO form) {
        AiChatWorkflowEntity entity = new AiChatWorkflowEntity();
        entity.setCode(form.getCode());
        entity.setName(form.getName());
        entity.setType(form.getType());
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        entity.setConfig(buildWorkflowCatalogConfig(form));
        workflowMapper.insert(entity);

        AiChatWorkflowConfigEntity configEntity = new AiChatWorkflowConfigEntity();
        configEntity.setCode(form.getCode() + "-default");
        configEntity.setWorkflowCode(form.getCode());
        configEntity.setName(form.getName() + "默认配置");
        configEntity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        WorkflowRuntimeConfig runtimeConfig = new WorkflowRuntimeConfig();
        configEntity.setConfig(runtimeConfig);
        workflowConfigMapper.insert(configEntity);

        return detail(form.getKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiFlowDetailDTO updateWorkflow(Long id, AiFlowWorkflowFormDTO form) {
        AiChatWorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        entity.setName(form.getName());
        entity.setType(form.getType());
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        entity.setConfig(buildWorkflowCatalogConfig(form));
        workflowMapper.updateById(entity);
        return detail(resolveRouteKey(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWorkflow(Long id) {
        AiChatWorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            return Boolean.FALSE;
        }
        List<AiChatWorkflowConfigEntity> configs = workflowConfigMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigEntity>()
                .eq(AiChatWorkflowConfigEntity::getWorkflowCode, entity.getCode()));
        for (AiChatWorkflowConfigEntity config : configs) {
            List<AiChatWorkflowConfigNodeEntity> configNodes = configNodeMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigNodeEntity>()
                    .eq(AiChatWorkflowConfigNodeEntity::getConfigCode, config.getCode()));
            for (AiChatWorkflowConfigNodeEntity configNode : configNodes) {
                configNodeSkillMapper.delete(new LambdaQueryWrapper<AiChatWorkflowConfigNodeSkillEntity>()
                        .eq(AiChatWorkflowConfigNodeSkillEntity::getConfigCode, config.getCode())
                        .eq(AiChatWorkflowConfigNodeSkillEntity::getNodeCode, configNode.getNodeCode()));
                configNodeMapper.deleteById(configNode.getId());
            }
            workflowConfigMapper.deleteById(config.getId());
        }
        return workflowMapper.deleteById(id) > 0;
    }

    private AiFlowCardDTO buildWorkflowCard(AiChatWorkflowEntity workflow,
                                            AiChatWorkflowConfigEntity config,
                                            Map<String, List<AiChatWorkflowConfigNodeEntity>> configNodesByConfigCode,
                                            List<AiChatNodeEntity> allNodes) {
        Map<String, AiChatNodeEntity> nodeByCode = allNodes.stream()
                .collect(Collectors.toMap(AiChatNodeEntity::getCode, item -> item, (left, right) -> left));
        AiFlowCardDTO card = new AiFlowCardDTO();
        card.setId(workflow.getId());
        card.setKey(resolveRouteKey(workflow));
        card.setCode(workflow.getCode());
        card.setName(workflow.getName());
        card.setType(workflow.getType());
        card.setScene(workflow.getConfig() == null ? "" : workflow.getConfig().getSceneDesc());
        card.setTags(workflow.getConfig() == null ? new ArrayList<>() : workflow.getConfig().getTags());
        card.setStatus(resolveStatus(workflow.getEnabled(), "已接入", "待补充"));
        List<AiChatWorkflowConfigNodeEntity> configNodes = config == null ? List.of() : configNodesByConfigCode.getOrDefault(config.getCode(), List.of());
        String nodes = configNodes.stream()
                .sorted(Comparator.comparing(item -> item.getSort() == null ? Integer.MAX_VALUE : item.getSort()))
                .map(item -> {
                    AiChatNodeEntity node = nodeByCode.get(item.getNodeCode());
                    return node == null ? item.getNodeCode() : node.getName();
                })
                .collect(Collectors.joining(" -> "));
        card.setNodes(nodes.isBlank() ? "-" : nodes);
        return card;
    }

    private AiFlowCardDTO buildNodeCard(AiChatNodeEntity node,
                                        Map<String, AiChatWorkflowEntity> workflowByCode,
                                        List<AiChatWorkflowConfigEntity> configs,
                                        List<AiChatWorkflowConfigNodeEntity> configNodes) {
        List<String> workflowNames = new ArrayList<>();
        for (AiChatWorkflowConfigNodeEntity configNode : configNodes) {
            if (!Objects.equals(configNode.getNodeCode(), node.getCode())) {
                continue;
            }
            for (AiChatWorkflowConfigEntity config : configs) {
                if (Objects.equals(config.getCode(), configNode.getConfigCode())) {
                    AiChatWorkflowEntity workflow = workflowByCode.get(config.getWorkflowCode());
                    if (workflow != null) {
                        workflowNames.add(workflow.getName());
                    }
                }
            }
        }
        AiFlowCardDTO card = new AiFlowCardDTO();
        card.setId(node.getId());
        card.setKey(node.getCode());
        card.setCode(node.getCode());
        card.setName(node.getName());
        card.setType(node.getType());
        card.setScene(node.getConfig() == null ? "" : node.getConfig().getSummary());
        card.setNodes(workflowNames.isEmpty() ? "适用流程：-" : "适用流程：" + String.join("、", workflowNames.stream().distinct().toList()));
        card.setStatus(resolveStatus(node.getEnabled(), "已接入", "待补充"));
        card.setTags(List.of(node.getType()));
        return card;
    }

    private AiFlowCardDTO buildSkillCard(AiChatSkillEntity skill, Map<String, List<AiChatWorkflowConfigNodeSkillEntity>> skillBindingsByNodeCode) {
        AiFlowCardDTO card = new AiFlowCardDTO();
        card.setId(skill.getId());
        card.setKey(skill.getCode());
        card.setCode(skill.getCode());
        card.setName(skill.getName());
        card.setType(skill.getType());
        card.setScene(skill.getConfig() == null ? "" : skill.getConfig().getSummary());
        String nodes = skill.getConfig() == null || skill.getConfig().getSupportedPhases().isEmpty()
                ? "典型阶段：-"
                : "典型阶段：" + String.join("、", skill.getConfig().getSupportedPhases());
        card.setNodes(nodes);
        card.setStatus(resolveStatus(skill.getEnabled(), "已接入", "待补充"));
        card.setTags(List.of(skill.getType()));
        return card;
    }

    private AiFlowNodeDetailDTO buildNodeDetail(AiChatWorkflowConfigNodeEntity configNode,
                                                AiChatNodeEntity node,
                                                List<AiChatWorkflowConfigNodeSkillEntity> bindings,
                                                Map<String, AiChatSkillEntity> skillByCode) {
        WorkflowNodeRuntimeConfig runtimeConfig = configNode.getConfig() == null ? new WorkflowNodeRuntimeConfig() : configNode.getConfig();
        AiFlowNodeDetailDTO detail = new AiFlowNodeDetailDTO();
        detail.setId(configNode.getId());
        detail.setConfigCode(configNode.getConfigCode());
        detail.setNodeCode(configNode.getNodeCode());
        detail.setKey(configNode.getNodeCode());
        detail.setName(node == null ? configNode.getNodeCode() : node.getName());
        detail.setType(node == null ? "" : node.getType());
        detail.setStatus(resolveStatus(configNode.getEnabled(), "启用", "停用"));
        detail.setMode(runtimeConfig.getExecuteMode());
        detail.setNextCode(configNode.getNextCode());
        detail.setSort(configNode.getSort());
        detail.setSummary(runtimeConfig.getSummary());
        detail.setInputDefinitions(runtimeConfig.getInputDefinitions() == null ? new ArrayList<>() : runtimeConfig.getInputDefinitions());
        detail.setOutputDefinitions(runtimeConfig.getOutputDefinitions() == null ? new ArrayList<>() : runtimeConfig.getOutputDefinitions());
        detail.setConfigItems(runtimeConfig.getConfigItems() == null ? new ArrayList<>() : runtimeConfig.getConfigItems());

        List<AiFlowNodeSkillItemDTO> skillItems = new ArrayList<>();
        if (bindings != null) {
            for (AiChatWorkflowConfigNodeSkillEntity binding : bindings) {
                AiChatSkillEntity skill = skillByCode.get(binding.getSkillCode());
                AiFlowNodeSkillItemDTO item = new AiFlowNodeSkillItemDTO();
                item.setId(binding.getId());
                item.setKey(binding.getSkillCode());
                item.setName(skill == null ? binding.getSkillCode() : skill.getName());
                item.setPhase(binding.getPhase());
                item.setStatus(resolveStatus(binding.getEnabled(), "已挂接", "未挂接"));
                item.setSummary(skill == null || skill.getConfig() == null ? "" : skill.getConfig().getSummary());
                skillItems.add(item);
            }
        }
        detail.setSkillItems(skillItems);
        return detail;
    }

    private List<AiChatWorkflowEntity> listAllWorkflows() {
        return workflowMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowEntity>().orderByAsc(AiChatWorkflowEntity::getId));
    }

    private List<AiChatNodeEntity> listAllNodes() {
        return nodeMapper.selectList(new LambdaQueryWrapper<AiChatNodeEntity>().orderByAsc(AiChatNodeEntity::getId));
    }

    private List<AiChatSkillEntity> listAllSkills() {
        return skillMapper.selectList(new LambdaQueryWrapper<AiChatSkillEntity>().orderByAsc(AiChatSkillEntity::getId));
    }

    private List<AiChatWorkflowConfigEntity> listAllConfigs() {
        return workflowConfigMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigEntity>().orderByAsc(AiChatWorkflowConfigEntity::getId));
    }

    private List<AiChatWorkflowConfigNodeEntity> listAllConfigNodes() {
        return configNodeMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigNodeEntity>().orderByAsc(AiChatWorkflowConfigNodeEntity::getId));
    }

    private List<AiChatWorkflowConfigNodeSkillEntity> listAllConfigNodeSkills() {
        return configNodeSkillMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigNodeSkillEntity>().orderByAsc(AiChatWorkflowConfigNodeSkillEntity::getId));
    }

    private AiChatWorkflowEntity findWorkflowByRouteKey(String workflowKey) {
        return listAllWorkflows().stream()
                .filter(item -> Objects.equals(resolveRouteKey(item), workflowKey))
                .findFirst()
                .orElse(null);
    }

    private AiChatWorkflowConfigEntity findPrimaryConfig(String workflowCode) {
        return workflowConfigMapper.selectList(new LambdaQueryWrapper<AiChatWorkflowConfigEntity>()
                        .eq(AiChatWorkflowConfigEntity::getWorkflowCode, workflowCode)
                        .orderByAsc(AiChatWorkflowConfigEntity::getId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private WorkflowCatalogConfig buildWorkflowCatalogConfig(AiFlowWorkflowFormDTO form) {
        WorkflowCatalogConfig config = new WorkflowCatalogConfig();
        config.setRouteKey(form.getKey());
        config.setSceneDesc(form.getScene());
        config.setTags(form.getTags() == null ? new ArrayList<>() : form.getTags());
        return config;
    }

    private String resolveRouteKey(AiChatWorkflowEntity workflow) {
        if (workflow.getConfig() != null && workflow.getConfig().getRouteKey() != null && !workflow.getConfig().getRouteKey().isBlank()) {
            return workflow.getConfig().getRouteKey();
        }
        return workflow.getCode();
    }

    private String resolveStatus(Boolean enabled, String enabledLabel, String disabledLabel) {
        return Boolean.TRUE.equals(enabled) ? enabledLabel : disabledLabel;
    }
}
