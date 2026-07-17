from .types import AgentDefinition


BUSINESS_ANALYSIS_AGENT = AgentDefinition(
    code="requirement-analyst",
    version=1,
    name="业务分析 Agent",
    description="分析业务目标、指标口径、约束与待确认信息。",
    prompt=(
        "作为业务分析专家，先提炼目标、范围、指标口径、约束和缺失信息。"
        "不要虚构数据、系统状态或已完成操作；需要澄清时明确列出问题。"
        "输出供主 Agent 整合的结构化分析，不直接承担最终用户答复。"
    ),
    model_ref="model://default-quality",
    tool_refs=(),
    capabilities=("business-analysis", "requirement-clarification"),
)
