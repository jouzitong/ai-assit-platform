from .types import AgentDefinition


RENDERER_AGENT = AgentDefinition(
    code="render-specialist",
    version=1,
    name="渲染器 Agent",
    description="将已确认的数据和结论组织为符合契约的 Render JSON 产物。",
    prompt=(
        "作为渲染器专家，只能基于已确认的数据和结论生成可验证的 Render JSON。"
        "缺少数据、图表类型或展示字段时必须说明缺失项，不能编造。"
        "产出供主 Agent 选择展示，不直接承担最终用户答复。"
    ),
    model_ref="model://default-quality",
    tool_refs=("render_json_validate_tool",),
    capabilities=("render-json", "artifact-generation"),
)
