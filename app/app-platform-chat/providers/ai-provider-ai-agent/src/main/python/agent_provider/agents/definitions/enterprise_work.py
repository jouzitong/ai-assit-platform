from .types import AgentDefinition, AgentDelegation


ENTERPRISE_WORK_AGENT = AgentDefinition(
    code="enterprise-work-assistant",
    version=1,
    name="企业工作助手",
    description="企业日常工作的主控 Agent，澄清需求、组织专业工作并交付可追溯结论。",
    prompt=(
        "你是企业工作助手，面向日常工作请求负责澄清目标、规划步骤、委派专业 Agent，并整合最终答复。"
        "数据查询、指标口径和异常分析交给 ask_data_analysis；看板、图表和轻应用草稿交给 "
        "ask_dashboard_application_builder；文件提取、摘要和对比交给 ask_document_analysis；"
        "企业制度、产品和项目知识问答交给 ask_knowledge_policy；表单、流程和待办交给 ask_workflow_forms。"
        "只在专业能力确有必要时委派，子 Agent 输出是工作材料，由你核验后交付。"
        "遇到问数请求时先区分用户只要分析结论，还是希望生成可交互看板/应用；如果两者无法确定，"
        "必须先追问，不能默认创建或发布页面。需要构建应用时委派看板与应用构建 Agent，"
        "并保留其 ApplicationBrief、DataContract、ApplicationPlan、RenderDocument 与 ValidationReport。"
        "任何写入、发布、提交、发起流程或改变业务数据的操作，都必须先明确影响范围并取得用户确认；"
        "未调用已授权工具时，不得声称已经查询数据、读取文件、填写表单或完成系统操作。"
    ),
    model_ref="model://runtime",
    tool_refs=("knowledge_base_search_tool",),
    capabilities=("conversation", "orchestration", "enterprise-work", "knowledge-retrieval"),
    agent_tools=(
        AgentDelegation(
            target_code="data-analysis",
            tool_name="ask_data_analysis",
            description="委派数据查询规划、指标口径、异常分析与业务结论。",
        ),
        AgentDelegation(
            target_code="dashboard-application-builder",
            tool_name="ask_dashboard_application_builder",
            description="委派看板、图表和轻应用草稿的设计与校验。",
        ),
        AgentDelegation(
            target_code="document-analysis",
            tool_name="ask_document_analysis",
            description="委派文件内容提取、摘要、对比和结构化分析。",
        ),
        AgentDelegation(
            target_code="knowledge-policy",
            tool_name="ask_knowledge_policy",
            description="委派企业知识、制度、产品和项目资料的循证问答。",
        ),
        AgentDelegation(
            target_code="workflow-forms",
            tool_name="ask_workflow_forms",
            description="委派表单填写、流程发起和待办处理的准备与核验。",
        ),
    ),
)
