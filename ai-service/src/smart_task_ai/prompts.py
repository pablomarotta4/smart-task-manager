from __future__ import annotations

from smart_task_ai.contracts import ProjectDraft, QualityReport

PLANNER_SYSTEM_PROMPT = """You are a pragmatic project planner.
Turn a short human brief into an actionable first project backlog.

Rules:
- Produce 4 to 8 tickets unless the brief genuinely needs only 3.
- Give every ticket a distinct outcome-oriented title. Do not repeat generic setup tasks.
- Make each description specific enough that an engineer or operator can begin work.
- Include at least two observable acceptance criteria per ticket.
- Use stable lowercase client IDs containing letters, numbers, and hyphens.
- Dependencies must reference earlier or parallel tickets by client ID and must not form cycles.
- Prefer a small end-to-end first release over speculative future work.
- State material assumptions and risks instead of inventing hidden requirements.
- Follow the supplied JSON schema exactly.
"""


def generation_prompt(project_brief: str) -> str:
    return f"""Create the first actionable project plan for this brief:

<project_brief>
{project_brief}
</project_brief>

The result must be useful for human review before any tickets are created.
"""


def revision_prompt(
    project_brief: str,
    draft: ProjectDraft,
    quality: QualityReport,
) -> str:
    issue_lines = "\n".join(
        f"- {issue.code}: {issue.message} Tickets: {', '.join(issue.ticket_ids) or 'project'}"
        for issue in quality.issues
    )
    return f"""Revise this draft once so it is specific, sufficient, and non-repetitive.

Original project brief:
<project_brief>
{project_brief}
</project_brief>

Deterministic quality findings (score {quality.score}/100):
{issue_lines}

Current draft:
<draft_json>
{draft.model_dump_json(indent=2)}
</draft_json>

Return the complete corrected draft, preserving good content while resolving every finding.
"""

