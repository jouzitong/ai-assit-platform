export const workflowTypes = [
  {
    key: 'query',
    name: 'AI问数流程',
    code: 'ai-query-workflow',
    scene: '面向智能问数、SQL 生成、指标分析和数据解释。',
    nodes: 'ChatMessage -> QueryPlanning -> KnowledgeSearch -> SqlGenerate -> SqlValidate -> SqlExecute -> Render',
    status: '已接入',
    tags: ['问数', 'SQL', '规划']
  },
  {
    key: 'chat',
    name: '通用对话流程',
    code: 'general-chat-workflow',
    scene: '面向普通问答、总结、改写和通用助手场景。',
    nodes: 'SessionPrepare -> PromptBuild -> ModelCall -> Render',
    status: '规划中',
    tags: ['对话', '总结', '通用']
  },
  {
    key: 'app',
    name: 'AI应用流程',
    code: 'ai-app-workflow',
    scene: '面向带工具编排、业务节点和多步骤执行的应用流程。',
    nodes: 'IntentRoute -> ToolPlan -> SkillExecute -> ResultAssemble -> Render',
    status: '规划中',
    tags: ['应用', '工具', '编排']
  },
  {
    key: 'audit',
    name: '流程审查与回放',
    code: 'workflow-audit',
    scene: '面向流程版本核对、节点回放、问题追踪和治理审计。',
    nodes: 'DefinitionLoad -> ArtifactReplay -> Review -> Report',
    status: '待补充',
    tags: ['审计', '回放', '治理']
  }
]

export const aiFlowSectionTabs = [
  { key: 'workflow', title: 'AI流程配置', desc: '流程入口与分类管理' },
  { key: 'node', title: '流程节点配置', desc: '节点编排与顺序维护' },
  { key: 'skill', title: 'Skill配置', desc: '挂载策略与执行阶段' }
]

const aiFlowSectionMetaMap = {
  workflow: {
    eyebrow: '流程类型列表',
    title: '先按流程分类管理，再进入具体定义',
    countLabel: '个流程类型',
    rows: workflowTypes
  },
  node: {
    eyebrow: '节点类型列表',
    title: '先看节点能力分类，再进入具体流程',
    countLabel: '类节点配置',
    rows: [
      {
        key: 'chat-message-node',
        name: '会话初始化节点',
        code: 'chat-message-node',
        scene: '负责初始化会话、轮次、消息上下文和基础元数据。',
        nodes: '适用流程：AI问数、通用对话、AI应用',
        status: '已接入',
        tags: ['上下文', '初始化', '消息']
      },
      {
        key: 'query-planning-node',
        name: '查询规划节点',
        code: 'query-planning-node',
        scene: '负责拆解用户目标、明确指标口径和检索 / SQL 生成方向。',
        nodes: '适用流程：AI问数',
        status: '已接入',
        tags: ['规划', '问数', '指标']
      },
      {
        key: 'skill-execute-node',
        name: '技能执行节点',
        code: 'skill-execute-node',
        scene: '负责在应用流程中执行工具、skill 和多步骤动作编排。',
        nodes: '适用流程：AI应用',
        status: '规划中',
        tags: ['技能', '工具', '执行']
      },
      {
        key: 'review-report-node',
        name: '审查报告节点',
        code: 'review-report-node',
        scene: '负责回放、审查、问题归因和最终报告输出。',
        nodes: '适用流程：流程审查与回放',
        status: '待补充',
        tags: ['审查', '回放', '报告']
      }
    ]
  },
  skill: {
    eyebrow: 'Skill挂载列表',
    title: '先看 Skill 分类，再进入挂载定义',
    countLabel: '类Skill配置',
    rows: [
      {
        key: 'before-execute-skill',
        name: '前置处理 Skill',
        code: 'before-execute-skill',
        scene: '在节点执行前做输入清洗、术语补全、偏好解析和规则注入。',
        nodes: '典型阶段：BEFORE_EXECUTE',
        status: '已接入',
        tags: ['前置', '清洗', '约束']
      },
      {
        key: 'after-execute-skill',
        name: '后置处理 Skill',
        code: 'after-execute-skill',
        scene: '在节点执行后做结果聚合、输出润色、摘要生成和执行审计。',
        nodes: '典型阶段：AFTER_EXECUTE',
        status: '规划中',
        tags: ['后置', '聚合', '润色']
      },
      {
        key: 'review-output-skill',
        name: '结果审查 Skill',
        code: 'review-output-skill',
        scene: '对规划结果、SQL 结果和最终回答做结构及风险审查。',
        nodes: '典型阶段：REVIEW_OUTPUT',
        status: '已接入',
        tags: ['审查', '质量', '风险']
      }
    ]
  }
}

export function getWorkflowTypeByKey(key) {
  return workflowTypes.find(item => item.key === key)
}

export function getAiFlowSectionMeta(sectionKey) {
  return aiFlowSectionMetaMap[sectionKey] || aiFlowSectionMetaMap.workflow
}
