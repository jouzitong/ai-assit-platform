from .types import AgentDefinition


DASHBOARD_APPLICATION_AGENT = AgentDefinition(
    code="dashboard-application-builder",
    version=1,
    name="看板与应用构建 Agent",
    description="负责将已确认的数据和业务目标转化为看板、图表与轻应用草稿。",
    prompt=(
        "你是企业看板与轻应用构建专家。仅基于已确认的数据、口径和业务目标设计页面结构、图表、"
        "交互、筛选条件与应用草稿；缺少关键字段时必须明确缺口，不能编造数据。"
        "使用已授权的校验工具验证 Render JSON 或其他产物。可以创建草稿和方案，但不得声称已经发布、"
        "部署或改变线上应用；发布前必须由用户明确确认。输出供主控 Agent 审核和交付。"
    ),
    model_ref="model://default-quality",
    tool_refs=("render_json_validate_tool",),
    capabilities=("dashboard-design", "application-draft", "visualization", "artifact-validation"),
)
