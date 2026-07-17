-- Published HOME_CHAT multi-Agent bootstrap. Every reference below is an explicit published v1 identity.
-- Seed DML is insert-only: rerunning this file never re-enables or overwrites administrator-managed rows.
SET @home_chat_workflow_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"ArtifactWorkflow","metadata":{"code":"home-chat-output","version":1,"name":"首页回答验收规范","description":"确保首页 Agent 返回非空、可展示的最终回答","labels":{"seed":"true"}},"spec":{"artifacts":[{"code":"final-answer","name":"最终回答","artifactType":"TEXT","contentFormat":"MARKDOWN","required":true,"visible":false,"inlineSchema":{"type":"string"}}],"checks":[{"code":"final-answer-schema","name":"最终回答结构检查","targetArtifact":"final-answer","checkerType":"JSON_SCHEMA","severity":"ERROR","blocking":true,"retryable":true,"config":{}}],"completionPolicy":{"requireAllRequiredArtifacts":true,"requireAllBlockingChecksPassed":true},"repairPolicy":{"maxRepairAttempts":1,"onExhausted":"INPUT_REQUIRED"}}}';
SET @home_chat_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"Agent","metadata":{"code":"home-assistant","version":1,"name":"首页智能助手","description":"理解用户目标，按需调用专业智能体，并负责最终答复。","labels":{"entry":"HOME_CHAT","seed":"true"}},"spec":{"instructions":{"type":"inline","text":"你是平台首页智能任务助手，也是唯一面向用户的答复所有者。先理解用户目标；问候、身份询问、常识问答或无需专业能力的问题必须直接用自然语言回答，禁止调用专业智能体。只有任务确实需要专业能力时，才把已配置的专业智能体作为工具调用，其返回仅作为内部材料。只有已经存在候选产出物且确实需要质量复核时，才调用 review_result。无论调用了哪些工具，最终都必须由你整合为清晰、可验证的 Markdown 答复；不得把专业智能体的结构化检查报告、工具原始返回或内部协议 JSON 原样展示给用户。"},"model":{"ref":"model://default-quality","settings":{"temperature":0.2}},"skillRefs":[],"toolRefs":[],"knowledgeRefs":[],"mcpRefs":[],"collaboration":{"agentTools":[{"targetAgentRef":"agent://requirement-analyst/v1","mode":"AS_TOOL","toolName":"analyze_requirement","description":"分析复杂需求和缺失信息"},{"targetAgentRef":"agent://sql-specialist/v1","mode":"AS_TOOL","toolName":"plan_and_generate_sql","description":"规划数据查询并生成安全的候选 SQL"},{"targetAgentRef":"agent://render-specialist/v1","mode":"AS_TOOL","toolName":"build_render","description":"生成可验收的 Render JSON 产出物"},{"targetAgentRef":"agent://result-reviewer/v1","mode":"AS_TOOL","toolName":"review_result","description":"仅复核已经生成的候选产出物及其验收标准"}],"handoffs":[]},"guardrails":{"input":[],"output":[]},"output":{"mode":"artifactSet","workflowRef":"workflow://home-chat-output/v1","schema":{}},"runtimeDefaults":{"maxTurns":12,"timeoutMs":120000,"maxAgentDepth":4,"toolConcurrency":4,"stateStrategy":"applicationReplay","tracing":{"enabled":true,"includeSensitiveData":false,"workflowName":"HOME_CHAT"}},"extensions":{}}}';
SET @requirement_agent_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"Agent","metadata":{"code":"requirement-analyst","version":1,"name":"需求分析智能体","description":"分析目标、约束、业务术语和缺失信息，返回结构化结论。","labels":{"seed":"true","specialty":"requirement"}},"spec":{"instructions":{"type":"inline","text":"分析目标、约束、业务术语和缺失信息，返回结构化结论。"},"model":{"ref":"model://default-quality","settings":{"temperature":0.2}},"skillRefs":[],"toolRefs":[],"knowledgeRefs":[],"mcpRefs":[],"collaboration":{"agentTools":[],"handoffs":[]},"guardrails":{"input":[],"output":[]},"output":{"mode":"text","workflowRef":null,"schema":{}},"runtimeDefaults":{"maxTurns":12,"timeoutMs":120000,"maxAgentDepth":4,"toolConcurrency":4,"stateStrategy":"applicationReplay","tracing":{"enabled":true,"includeSensitiveData":false}},"extensions":{}}}';
SET @sql_agent_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"Agent","metadata":{"code":"sql-specialist","version":1,"name":"SQL 专业智能体","description":"根据已授权的数据能力规划查询并生成只读、安全、可解释的候选 SQL。","labels":{"seed":"true","specialty":"sql"}},"spec":{"instructions":{"type":"inline","text":"根据已授权的数据能力规划查询并生成只读、安全、可解释的候选 SQL。"},"model":{"ref":"model://default-quality","settings":{"temperature":0.2}},"skillRefs":[],"toolRefs":[],"knowledgeRefs":[],"mcpRefs":[],"collaboration":{"agentTools":[],"handoffs":[]},"guardrails":{"input":[],"output":[]},"output":{"mode":"text","workflowRef":null,"schema":{}},"runtimeDefaults":{"maxTurns":12,"timeoutMs":120000,"maxAgentDepth":4,"toolConcurrency":4,"stateStrategy":"applicationReplay","tracing":{"enabled":true,"includeSensitiveData":false}},"extensions":{}}}';
SET @render_agent_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"Agent","metadata":{"code":"render-specialist","version":1,"name":"渲染专业智能体","description":"根据用户目标和已确认数据生成符合 Render JSON 契约的产出物。","labels":{"seed":"true","specialty":"render"}},"spec":{"instructions":{"type":"inline","text":"根据用户目标和已确认数据生成符合 Render JSON 契约的产出物。"},"model":{"ref":"model://default-quality","settings":{"temperature":0.2}},"skillRefs":[],"toolRefs":[],"knowledgeRefs":[],"mcpRefs":[],"collaboration":{"agentTools":[],"handoffs":[]},"guardrails":{"input":[],"output":[]},"output":{"mode":"text","workflowRef":null,"schema":{}},"runtimeDefaults":{"maxTurns":12,"timeoutMs":120000,"maxAgentDepth":4,"toolConcurrency":4,"stateStrategy":"applicationReplay","tracing":{"enabled":true,"includeSensitiveData":false}},"extensions":{}}}';
SET @reviewer_agent_manifest = '{"apiVersion":"ai.platform/v1alpha1","kind":"Agent","metadata":{"code":"result-reviewer","version":1,"name":"结果复核智能体","description":"只输出结构化检查报告，不静默修改被检查的产出物。","labels":{"seed":"true","specialty":"review"}},"spec":{"instructions":{"type":"inline","text":"你是仅供上层 Agent 调用的结果复核智能体。输入必须包含待复核候选产出物及其验收标准；如果没有候选产出物，明确返回不可复核，不回答原始用户问题。只输出包含结论、问题和改进建议的结构化检查报告，不静默修改被检查的产出物，也不承担面向用户的最终答复。"},"model":{"ref":"model://default-quality","settings":{"temperature":0.2}},"skillRefs":[],"toolRefs":[],"knowledgeRefs":[],"mcpRefs":[],"collaboration":{"agentTools":[],"handoffs":[]},"guardrails":{"input":[],"output":[]},"output":{"mode":"text","workflowRef":null,"schema":{}},"runtimeDefaults":{"maxTurns":12,"timeoutMs":120000,"maxAgentDepth":4,"toolConcurrency":4,"stateStrategy":"applicationReplay","tracing":{"enabled":true,"includeSensitiveData":false}},"extensions":{}}}';
SET @seed_validation = '{"valid":true,"compatible":true,"errors":[],"issues":[],"warnings":[],"message":"validation passed"}';

INSERT INTO `agent_workflow`
  (`code`, `name`, `type`, `enabled`, `config`, `deleted`, `version`)
VALUES
  ('home-chat-output', '首页回答验收规范', 'ARTIFACT_ACCEPTANCE', TRUE, '{}', 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `agent_workflow_version`
  (`workflow_code`, `version_no`, `status`, `specification_json`, `validation_json`, `checksum`, `published_at`, `deleted`, `version`)
VALUES
  ('home-chat-output', 1, 3, @home_chat_workflow_manifest, @seed_validation,
   SHA2(@home_chat_workflow_manifest, 256), NOW(), 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `agent_definition`
  (`code`, `name`, `description`, `current_version`, `status`, `enabled`, `deleted`, `version`)
VALUES
  ('home-assistant', '首页智能助手', '理解用户目标，按需调用专业智能体，并负责最终答复。', 1, 3, TRUE, 0, 1),
  ('requirement-analyst', '需求分析智能体', '分析目标、约束、业务术语和缺失信息，返回结构化结论。', 1, 3, TRUE, 0, 1),
  ('sql-specialist', 'SQL 专业智能体', '根据已授权的数据能力规划查询并生成只读、安全、可解释的候选 SQL。', 1, 3, TRUE, 0, 1),
  ('render-specialist', '渲染专业智能体', '根据用户目标和已确认数据生成符合 Render JSON 契约的产出物。', 1, 3, TRUE, 0, 1),
  ('result-reviewer', '结果复核智能体', '只输出结构化检查报告，不静默修改被检查的产出物。', 1, 3, TRUE, 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `agent_definition_version`
  (`agent_code`, `version_no`, `status`, `manifest_json`, `validation_json`, `checksum`, `published_at`, `deleted`, `version`)
VALUES
  ('home-assistant', 1, 3, @home_chat_manifest, @seed_validation,
   SHA2(@home_chat_manifest, 256), NOW(), 0, 1),
  ('requirement-analyst', 1, 3, @requirement_agent_manifest, @seed_validation,
   SHA2(@requirement_agent_manifest, 256), NOW(), 0, 1),
  ('sql-specialist', 1, 3, @sql_agent_manifest, @seed_validation,
   SHA2(@sql_agent_manifest, 256), NOW(), 0, 1),
  ('render-specialist', 1, 3, @render_agent_manifest, @seed_validation,
   SHA2(@render_agent_manifest, 256), NOW(), 0, 1),
  ('result-reviewer', 1, 3, @reviewer_agent_manifest, @seed_validation,
   SHA2(@reviewer_agent_manifest, 256), NOW(), 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `agent_entry_binding`
  (`entry_code`, `agent_code`, `agent_version`, `runtime_type`, `sdk_version`, `priority`, `enabled`, `config_json`, `deleted`, `version`)
VALUES
  ('HOME_CHAT', 'home-assistant', 1, 1, 'latest-compatible', 10, TRUE, '{}', 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

SET @home_chat_manifest = NULL;
SET @home_chat_workflow_manifest = NULL;
SET @requirement_agent_manifest = NULL;
SET @sql_agent_manifest = NULL;
SET @render_agent_manifest = NULL;
SET @reviewer_agent_manifest = NULL;
SET @seed_validation = NULL;
