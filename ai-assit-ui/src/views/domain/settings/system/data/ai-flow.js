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

export function getWorkflowTypeByKey(key) {
  return workflowTypes.find(item => item.key === key)
}
