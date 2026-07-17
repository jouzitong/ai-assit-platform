"""Versioned enterprise work Agent definitions used by the local catalog."""

from .dashboard_application import DASHBOARD_APPLICATION_AGENT
from .data_analysis import DATA_ANALYSIS_AGENT
from .document_analysis import DOCUMENT_ANALYSIS_AGENT
from .enterprise_work import ENTERPRISE_WORK_AGENT
from .knowledge_policy import KNOWLEDGE_POLICY_AGENT
from .types import AgentDefinition, AgentDelegation
from .workflow_forms import WORKFLOW_FORMS_AGENT

__all__ = [
    "AgentDefinition",
    "AgentDelegation",
    "ENTERPRISE_WORK_AGENT",
    "DATA_ANALYSIS_AGENT",
    "DASHBOARD_APPLICATION_AGENT",
    "DOCUMENT_ANALYSIS_AGENT",
    "KNOWLEDGE_POLICY_AGENT",
    "WORKFLOW_FORMS_AGENT",
]
