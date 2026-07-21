from __future__ import annotations

from typing import Any


def build_skill_tool(
    graph: Any,
    emitter: Any,
    function_tool: Any,
    allowed_refs: list[str],
) -> Any:
    def load_skill_resource(skill_ref: str, resource_path: str = "SKILL.md") -> dict[str, Any]:
        """Load an approved skill resource only when the current task needs it."""

        canonical = graph.skill_catalog.resolve(skill_ref).ref
        if canonical not in allowed_refs:
            raise ValueError(f"Skill is not assigned to this Agent: {skill_ref}")

        def loaded(record: Any, relative: str) -> None:
            emitter.event(
                "skill.loaded",
                status="SUCCESS",
                message=f"已加载技能：{record.name}",
                ext={
                    "activityCode": f"skill:{record.ref}:{relative}",
                    "activityType": "SKILL_LOAD",
                    "activityName": f"加载技能：{record.name}",
                    "skillRef": record.ref,
                    "skillName": record.name,
                    "resourcePath": relative,
                    "contentHash": record.content_hash,
                    "outputSummary": f"已加载技能“{record.name}”的资源：{relative}。",
                },
            )

        return graph.skill_catalog.read(canonical, resource_path, loaded)

    decorator = function_tool(
        name_override="load_skill_resource",
        description_override=(
            "Load SKILL.md, data, templates, or another resource from an approved skill package. "
            "Call only after selecting a skill from the metadata in the Agent instructions."
        ),
    )
    return decorator(load_skill_resource)
