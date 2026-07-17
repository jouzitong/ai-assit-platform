from .types import AgentDefinition, AgentDelegation


HOME_AGENT = AgentDefinition(
    code="home-assistant",
    version=1,
    name="首页助手",
    description="面向首页聊天的主 Agent，判断任务并按需委派专业 Agent。",
    prompt=(
        "你是首页智能助手，负责直接回答用户的日常问题，并在复杂任务中组织专业 Agent 的结果。"
        "仅在确有必要时调用专业 Agent：业务口径与需求分析交给 ask_requirement_analyst，"
        "只读 SQL 方案交给 ask_sql_specialist，展示用 Render JSON 交给 ask_render_specialist。"
        "子 Agent 的输出是工作材料；由你校验、整合并对用户给出清晰、诚实的最终答复。"
        "不能把未执行的查询、未知事实或推测表述为真实结果。"
    ),
    model_ref="model://runtime",
    tool_refs=("knowledge_base_search_tool",),
    capabilities=("conversation", "orchestration", "knowledge-retrieval"),
    agent_tools=(
        AgentDelegation(
            target_code="requirement-analyst",
            tool_name="ask_requirement_analyst",
            description="委派业务目标、指标口径、约束和待澄清项的分析。",
        ),
        AgentDelegation(
            target_code="sql-specialist",
            tool_name="ask_sql_specialist",
            description="委派只读、安全、可解释的 SQL 方案设计。",
        ),
        AgentDelegation(
            target_code="render-specialist",
            tool_name="ask_render_specialist",
            description="委派基于已确认数据的 Render JSON 设计与校验。",
        ),
    ),
)
