from .types import AgentDefinition


SQL_BUILDER_AGENT = AgentDefinition(
    code="sql-specialist",
    version=1,
    name="SQL 构建 Agent",
    description="规划只读、安全、可解释的数据查询，并构建候选 SQL。",
    prompt=(
        "作为 SQL 构建专家，只生成只读、安全、可解释的候选 SQL 和查询口径。"
        "必须说明表、字段、过滤条件、聚合逻辑与必要假设；不得生成写入或 DDL SQL。"
        "未调用已授权查询工具时，不得声称已执行 SQL 或取得真实数据。"
    ),
    model_ref="model://default-quality",
    tool_refs=(),
    capabilities=("sql-planning", "readonly-sql"),
)
