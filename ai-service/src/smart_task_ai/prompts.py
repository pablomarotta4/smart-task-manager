from __future__ import annotations

from smart_task_ai.contracts import ProjectDraft, QualityReport

PLANNER_SYSTEM_PROMPT = """You are a pragmatic project planner.
Turn a short human brief into an actionable first project backlog.

Rules:
- Produce 4 to 8 tickets unless the brief genuinely needs only 3.
- Every explicitly requested capability or user action must be implemented by at least one ticket.
- Do not count mentioning a capability in the objective, assumptions, or dates as implementing it.
- Before returning, verify that ticket descriptions and acceptance criteria cover
  every action in the brief.
- Give every ticket a distinct outcome-oriented title. Do not repeat generic setup tasks.
- Make each description specific enough that an engineer or operator can begin work.
- Include at least two observable acceptance criteria per ticket.
- Use stable lowercase client IDs containing letters, numbers, and hyphens.
- Dependencies must reference earlier or parallel tickets by client ID and must not form cycles.
- Prefer a small end-to-end first release over speculative future work.
- State material assumptions and risks instead of inventing hidden requirements.
- Follow the supplied JSON schema exactly.
"""

BRIEF_ANALYSIS_SYSTEM_PROMPT = """You extract explicit capabilities from a short project brief.
Return a concise checklist of user-visible actions and workflow steps stated in the brief.
Preserve the brief's action verbs and objects, such as "borrow tools" or "create a shopping list".
Do not include a broad instruction to build, create, or organize the overall product; extract the
specific outputs and workflow steps inside that goal instead.
Do not invent technical architecture, security, analytics, or operational requirements.
Combine only genuine synonyms; keep distinct workflow steps separate.
Follow the supplied JSON schema exactly.
"""


def generation_prompt(project_brief: str, explicit_capabilities: list[str] | None = None) -> str:
    capability_lines = "\n".join(
        f"- {capability}" for capability in explicit_capabilities or []
    )
    checklist = (
        f"""
Mandatory explicit capability checklist:
{capability_lines}

Each checklist item must appear as implemented behavior in a ticket title, description, or
acceptance criteria. Do not replace checklist items with infrastructure or generic setup work.
Copy each checklist action verb and object together into at least one ticket so coverage can be
verified deterministically.
"""
        if capability_lines
        else ""
    )
    return f"""Create the first actionable project plan for this brief:

<project_brief>
{project_brief}
</project_brief>
{checklist}

The result must be useful for human review before any tickets are created.
"""


def brief_analysis_prompt(project_brief: str) -> str:
    return f"""Extract the explicit capability checklist from this brief:
<project_brief>
{project_brief}
</project_brief>
"""


def revision_prompt(
    project_brief: str,
    draft: ProjectDraft,
    quality: QualityReport,
    explicit_capabilities: list[str] | None = None,
) -> str:
    issue_lines = "\n".join(
        f"- {issue.code}: {issue.message} Tickets: {', '.join(issue.ticket_ids) or 'project'}"
        for issue in quality.issues
    )
    capability_lines = "\n".join(
        f"- {capability}" for capability in explicit_capabilities or []
    )
    checklist = (
        f"\nMandatory explicit capabilities that must remain covered:\n{capability_lines}\n"
        if capability_lines
        else ""
    )
    return f"""Revise this draft once so it is specific, sufficient, and non-repetitive.

Original project brief:
<project_brief>
{project_brief}
</project_brief>
{checklist}

Deterministic quality findings (score {quality.score}/100):
{issue_lines}

Current draft:
<draft_json>
{draft.model_dump_json(indent=2)}
</draft_json>

Return the complete corrected draft, preserving good content while resolving every finding.
"""
