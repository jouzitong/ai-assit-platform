export function createWorkflowSeed(workflowKey) {
  if (workflowKey === 'query') {
    return [
      {
        key: 'chat-message',
        name: 'ChatMessageNode',
        type: '会话初始化',
        status: '启用',
        mode: '串行',
        summary: '初始化会话、轮次和用户消息，为后续节点准备完整执行上下文。',
        configItems: [
          { key: 'system-message', name: '系统提示消息', type: '提示消息', summary: '定义进入节点时发送给 AI 的系统级提示内容。', status: '启用' },
          { key: 'user-message-template', name: '用户消息模板', type: '提示消息', summary: '约束当前用户消息和上下文字段的拼装格式。', status: '启用' },
          { key: 'output-schema', name: '返回数据结构', type: '输出结构', summary: '定义 message、session、round 等字段的返回结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'default-pass-through', name: '默认透传', phase: '默认', summary: '当前节点默认不挂接强制 skill，保持消息初始化主路径稳定。', status: '未挂接' },
          { key: 'input-normalize', name: '输入清洗', phase: 'BEFORE_EXECUTE', summary: '在消息入上下文前统一清理空白、格式和异常字符。', status: '可扩展' },
          { key: 'message-preprocess', name: '消息预处理', phase: 'BEFORE_EXECUTE', summary: '在会话初始化前补充额外字段或路由标签。', status: '可扩展' }
        ]
      },
      {
        key: 'query-planning',
        name: 'QueryPlanningNode',
        type: '查询规划',
        status: '启用',
        mode: '串行',
        summary: '解析用户目标并生成结构化查询规划，决定知识检索和 SQL 生成方向。',
        configItems: [
          { key: 'planning-system-prompt', name: '规划提示消息', type: '提示消息', summary: '定义查询规划节点使用的 system prompt。', status: '启用' },
          { key: 'planning-user-template', name: '规划输入模板', type: '提示消息', summary: '定义历史消息、当前问题和补充上下文的组装格式。', status: '启用' },
          { key: 'planning-schema', name: '规划返回数据结构', type: '输出结构', summary: '定义 userGoal、analysisSummary、sqlFocus 等结构字段。', status: '启用' }
        ],
        skillItems: [
          { key: 'business-term-resolve', name: '术语解析', phase: 'BEFORE_EXECUTE', summary: '抽取业务术语并回填给查询规划输入。', status: '已挂接' },
          { key: 'time-range-normalize', name: '时间范围归一化', phase: 'BEFORE_EXECUTE', summary: '将自然语言时间转换成标准化时间范围。', status: '已挂接' },
          { key: 'query-plan-review', name: '查询规划审查', phase: 'REVIEW_OUTPUT', summary: '对规划结果做结构和风险审查。', status: '已挂接' }
        ]
      },
      {
        key: 'knowledge-search',
        name: 'KnowledgeSearchNode',
        type: '知识检索',
        status: '启用',
        mode: '串行',
        summary: '补充知识库口径、模型概览和外部上下文，为 SQL 生成节点提供辅助信息。',
        configItems: [
          { key: 'knowledge-query-template', name: '检索请求模板', type: '提示消息', summary: '定义知识库查询串的拼装规则。', status: '启用' },
          { key: 'knowledge-result-schema', name: '检索返回数据结构', type: '输出结构', summary: '定义知识命中摘要、metadata 和兜底结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'retrieval-rerank', name: '检索重排', phase: 'AFTER_EXECUTE', summary: '对知识检索命中结果进行重排和裁剪。', status: '可扩展' },
          { key: 'term-enrich', name: '术语补全', phase: 'BEFORE_EXECUTE', summary: '在发起知识检索前补齐同义词和口径别名。', status: '可扩展' }
        ]
      },
      {
        key: 'sql-generate',
        name: 'SqlGenerateNode',
        type: 'SQL 生成',
        status: '启用',
        mode: '串行',
        summary: '基于规划结果、知识上下文、SQL 规则和用户偏好生成候选 SQL。',
        configItems: [
          { key: 'sql-system-prompt', name: 'SQL 生成提示消息', type: '提示消息', summary: '定义 SQL 生成节点的 system prompt。', status: '启用' },
          { key: 'sql-user-template', name: 'SQL 输入模板', type: '提示消息', summary: '定义查询规划、知识上下文和偏好配置的拼装格式。', status: '启用' },
          { key: 'sql-output-schema', name: 'SQL 返回数据结构', type: '输出结构', summary: '定义 generatedSql、requestId 和扩展元数据结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'sql-generation-policy', name: 'SQL 生成规范', phase: 'BEFORE_EXECUTE', summary: '注入 SQL 硬约束、软规范和白名单规则。', status: '已挂接' },
          { key: 'user-preference-resolve', name: '用户偏好解析', phase: 'BEFORE_EXECUTE', summary: '提取用户偏好，补充 SQL 生成软约束。', status: '已挂接' }
        ]
      },
      {
        key: 'render',
        name: 'RenderNode',
        type: '结果渲染',
        status: '启用',
        mode: '串行',
        summary: '汇总规划、知识上下文和 SQL 预生成结果，生成最终回答并落库 assistant 消息。',
        configItems: [
          { key: 'render-system-prompt', name: '渲染提示消息', type: '提示消息', summary: '定义最终回答渲染节点使用的 system prompt。', status: '启用' },
          { key: 'render-input-template', name: '渲染输入模板', type: '提示消息', summary: '定义规划结果、SQL 预生成结果和知识上下文的拼装格式。', status: '启用' },
          { key: 'render-output-schema', name: '渲染返回数据结构', type: '输出结构', summary: '定义 renderedAnswer 和最终 artifact 的结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'output-polish', name: '输出润色', phase: 'AFTER_EXECUTE', summary: '对最终回答做语句润色和格式整理。', status: '可扩展' },
          { key: 'answer-review', name: '结果审查', phase: 'REVIEW_OUTPUT', summary: '检查回答是否覆盖关键信息与风险提示。', status: '可扩展' }
        ]
      }
    ]
  }

  if (workflowKey === 'chat') {
    return [
      {
        key: 'session-prepare',
        name: 'SessionPrepare',
        type: '会话准备',
        status: '规划中',
        mode: '串行',
        summary: '准备通用对话上下文。',
        configItems: [{ key: 'session-settings', name: '会话配置', type: '规则配置', summary: '维护基础会话参数、上下文窗口和初始化策略。', status: '规划中' }],
        skillItems: [{ key: 'session-default-skill', name: '上下文兜底', phase: '默认', summary: '后续在会话准备阶段统一挂接基础 skill。', status: '待补充' }]
      },
      {
        key: 'prompt-build',
        name: 'PromptBuild',
        type: '提示构建',
        status: '规划中',
        mode: '串行',
        summary: '构建通用对话 prompt。',
        configItems: [{ key: 'prompt-template', name: 'Prompt 模板', type: '提示消息', summary: '定义系统提示词、上下文拼装和输出结构。', status: '规划中' }],
        skillItems: [{ key: 'prompt-polish', name: 'Prompt 优化', phase: 'BEFORE_EXECUTE', summary: '补充角色信息和输出约束。', status: '待补充' }]
      },
      {
        key: 'model-call',
        name: 'ModelCall',
        type: '模型调用',
        status: '规划中',
        mode: '串行',
        summary: '执行模型调用。',
        configItems: [{ key: 'model-route', name: '模型路由', type: '规则配置', summary: '维护模型选择、重试和降级策略。', status: '规划中' }],
        skillItems: [{ key: 'model-review', name: '模型结果审查', phase: 'REVIEW_OUTPUT', summary: '审查模型输出结构和敏感内容。', status: '待补充' }]
      },
      {
        key: 'render',
        name: 'Render',
        type: '结果渲染',
        status: '规划中',
        mode: '串行',
        summary: '渲染通用对话结果。',
        configItems: [{ key: 'render-template', name: '输出模板', type: '输出结构', summary: '统一回复样式、引用信息和推荐项结构。', status: '规划中' }],
        skillItems: [{ key: 'answer-polish', name: '结果润色', phase: 'AFTER_EXECUTE', summary: '统一输出口吻、段落结构和结论风格。', status: '待补充' }]
      }
    ]
  }

  if (workflowKey === 'app') {
    return [
      {
        key: 'intent-route',
        name: 'IntentRoute',
        type: '意图路由',
        status: '规划中',
        mode: '串行',
        summary: '执行应用流程意图路由。',
        configItems: [{ key: 'intent-rules', name: '路由规则', type: '规则配置', summary: '定义应用入口意图识别和分流条件。', status: '规划中' }],
        skillItems: [{ key: 'intent-normalize', name: '意图归一化', phase: 'BEFORE_EXECUTE', summary: '统一用户输入中的业务意图描述。', status: '待补充' }]
      },
      {
        key: 'tool-plan',
        name: 'ToolPlan',
        type: '工具规划',
        status: '规划中',
        mode: '串行',
        summary: '规划工具调用顺序。',
        configItems: [{ key: 'tool-graph', name: '工具编排', type: '规则配置', summary: '定义工具依赖图、串并行策略和超时规则。', status: '规划中' }],
        skillItems: [{ key: 'tool-risk-review', name: '工具风险审查', phase: 'REVIEW_OUTPUT', summary: '对工具编排结果做权限和成本审查。', status: '待补充' }]
      },
      {
        key: 'skill-execute',
        name: 'SkillExecute',
        type: '技能执行',
        status: '规划中',
        mode: '并行/串行',
        summary: '执行技能节点。',
        configItems: [{ key: 'skill-runtime', name: '技能运行策略', type: '规则配置', summary: '定义技能超时、重试和日志追踪策略。', status: '规划中' }],
        skillItems: [{ key: 'skill-router', name: '技能路由', phase: 'BEFORE_EXECUTE', summary: '根据上下文选择待执行技能。', status: '待补充' }]
      },
      {
        key: 'result-assemble',
        name: 'ResultAssemble',
        type: '结果组装',
        status: '规划中',
        mode: '串行',
        summary: '组装应用流程结果。',
        configItems: [{ key: 'result-template', name: '结果模板', type: '输出结构', summary: '定义工具结果聚合结构和呈现顺序。', status: '规划中' }],
        skillItems: [{ key: 'result-merge', name: '结果合并', phase: 'AFTER_EXECUTE', summary: '对多技能返回结果做统一聚合。', status: '待补充' }]
      },
      {
        key: 'render',
        name: 'Render',
        type: '结果渲染',
        status: '规划中',
        mode: '串行',
        summary: '渲染应用结果。',
        configItems: [{ key: 'render-schema', name: '渲染模板', type: '输出结构', summary: '定义最终返回卡片、摘要和行动按钮结构。', status: '规划中' }],
        skillItems: [{ key: 'render-polish', name: '输出润色', phase: 'AFTER_EXECUTE', summary: '统一最终渲染样式和可读性。', status: '待补充' }]
      }
    ]
  }

  return [
    {
      key: 'definition-load',
      name: 'DefinitionLoad',
      type: '流程读取',
      status: '待补充',
      mode: '串行',
      summary: '读取流程定义。',
      configItems: [{ key: 'definition-source', name: '流程来源', type: '规则配置', summary: '维护流程定义加载来源和版本路由。', status: '待补充' }],
      skillItems: [{ key: 'definition-review', name: '流程读取审查', phase: 'REVIEW_OUTPUT', summary: '校验流程定义完整性和可回放性。', status: '待补充' }]
    },
    {
      key: 'artifact-replay',
      name: 'ArtifactReplay',
      type: '产物回放',
      status: '待补充',
      mode: '串行',
      summary: '回放历史产物。',
      configItems: [{ key: 'artifact-source', name: '回放来源', type: '规则配置', summary: '定义产物回放的数据源、时间范围和过滤条件。', status: '待补充' }],
      skillItems: [{ key: 'artifact-compare', name: '回放比对', phase: 'AFTER_EXECUTE', summary: '对回放产物和基线结果进行差异分析。', status: '待补充' }]
    },
    {
      key: 'review',
      name: 'Review',
      type: '流程审查',
      status: '待补充',
      mode: '串行',
      summary: '执行流程审查。',
      configItems: [{ key: 'review-rules', name: '审查规则', type: '规则配置', summary: '维护流程审查维度、风险等级和拦截条件。', status: '待补充' }],
      skillItems: [{ key: 'review-summary', name: '审查总结', phase: 'REVIEW_OUTPUT', summary: '生成流程审查总结和整改建议。', status: '待补充' }]
    },
    {
      key: 'report',
      name: 'Report',
      type: '结果报告',
      status: '待补充',
      mode: '串行',
      summary: '输出审查报告。',
      configItems: [{ key: 'report-template', name: '报告模板', type: '输出结构', summary: '定义回放和审查报告的章节结构。', status: '待补充' }],
      skillItems: [{ key: 'report-polish', name: '报告润色', phase: 'AFTER_EXECUTE', summary: '统一最终报告口径和摘要风格。', status: '待补充' }]
    }
  ]
}

export function cloneData(data) {
  return JSON.parse(JSON.stringify(data))
}

export function createNodeTemplateCatalog(workflowKey) {
  const baseTemplates = createWorkflowSeed(workflowKey)

  if (workflowKey === 'query') {
    baseTemplates.push(
      {
        key: 'result-explain',
        name: 'ResultExplainNode',
        type: '结果解释',
        status: '待补充',
        mode: '串行',
        summary: '对查询结果做业务解释、异常说明和口径补充。',
        configItems: [
          { key: 'explain-prompt', name: '解释提示消息', type: '提示消息', summary: '定义结果解释节点使用的提示模板。', status: '待补充' },
          { key: 'explain-schema', name: '解释返回结构', type: '输出结构', summary: '定义 explanation、insight 和 followUps 的结构。', status: '待补充' }
        ],
        skillItems: [
          { key: 'insight-polish', name: '洞察润色', phase: 'AFTER_EXECUTE', summary: '对结果解释内容做表达整理和重点提炼。', status: '待补充' }
        ]
      },
      {
        key: 'quality-check',
        name: 'QualityCheckNode',
        type: '质量审查',
        status: '待补充',
        mode: '回跳控制',
        summary: '对节点输出内容做完整性、准确性和格式质量检查。',
        configItems: [
          { key: 'quality-rules', name: '质量校验规则', type: '规则配置', summary: '定义完整性、格式和关键字段校验规则。', status: '待补充' }
        ],
        skillItems: [
          { key: 'quality-review', name: '质量审查 Skill', phase: 'REVIEW_OUTPUT', summary: '对节点输出做结构与业务质量审查。', status: '待补充' }
        ]
      }
    )
  }

  if (workflowKey === 'chat') {
    baseTemplates.push({
      key: 'memory-recall',
      name: 'MemoryRecall',
      type: '记忆召回',
      status: '待补充',
      mode: '串行',
      summary: '补充长期记忆、用户偏好和历史会话摘要。',
      configItems: [{ key: 'memory-source', name: '记忆来源', type: '规则配置', summary: '配置记忆检索来源与召回策略。', status: '待补充' }],
      skillItems: [{ key: 'memory-filter', name: '记忆过滤', phase: 'BEFORE_EXECUTE', summary: '对召回记忆做去噪和优先级排序。', status: '待补充' }]
    })
  }

  if (workflowKey === 'app') {
    baseTemplates.push({
      key: 'permission-check',
      name: 'PermissionCheck',
      type: '权限校验',
      status: '待补充',
      mode: '串行',
      summary: '在应用执行前校验工具权限、范围和用户身份。',
      configItems: [{ key: 'permission-rules', name: '权限规则', type: '规则配置', summary: '定义能力边界和资源访问条件。', status: '待补充' }],
      skillItems: [{ key: 'permission-audit', name: '权限审计', phase: 'REVIEW_OUTPUT', summary: '记录权限校验结果和风险提示。', status: '待补充' }]
    })
  }

  return baseTemplates
}

export function createSkillTemplateCatalog(workflowKey, node) {
  const baseTemplates = cloneData(node?.skillItems || [])

  if (workflowKey === 'query') {
    baseTemplates.push(
      {
        key: 'result-explain-polish',
        name: '结果解释润色',
        phase: 'AFTER_EXECUTE',
        summary: '对结果解释节点输出做语言压缩、重点提炼和业务口径补充。',
        status: '待补充'
      },
      {
        key: 'query-risk-review',
        name: '问数风险审查',
        phase: 'REVIEW_OUTPUT',
        summary: '对问数结果中的风险口径、空数据场景和异常提示做补充审查。',
        status: '待补充'
      }
    )
  }

  if (workflowKey === 'chat') {
    baseTemplates.push({
      key: 'persona-adapt',
      name: '人设适配',
      phase: 'BEFORE_EXECUTE',
      summary: '按用户偏好和会话角色补充对话风格约束。',
      status: '待补充'
    })
  }

  if (workflowKey === 'app') {
    baseTemplates.push({
      key: 'tool-permission-hint',
      name: '工具权限提醒',
      phase: 'REVIEW_OUTPUT',
      summary: '在应用流程结果返回前补充工具权限、耗时和风险提示。',
      status: '待补充'
    })
  }

  if (workflowKey === 'audit') {
    baseTemplates.push({
      key: 'audit-conclusion-polish',
      name: '审计结论润色',
      phase: 'AFTER_EXECUTE',
      summary: '统一审计回放结论的摘要结构和风险等级表达。',
      status: '待补充'
    })
  }

  const deduped = new Map()
  baseTemplates.forEach(item => {
    if (!deduped.has(item.key)) {
      deduped.set(item.key, item)
    }
  })

  return Array.from(deduped.values())
}
