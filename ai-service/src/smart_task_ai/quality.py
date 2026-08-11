from __future__ import annotations

import re
from collections import Counter
from difflib import SequenceMatcher
from itertools import combinations

from smart_task_ai.contracts import (
    PlanningContext,
    ProjectDraft,
    QualityIssue,
    QualityMetrics,
    QualityReport,
)

PASSING_SCORE = 75
SIMILARITY_THRESHOLD = 0.82
MIN_USEFUL_DESCRIPTION_LENGTH = 60
MIN_USEFUL_CRITERIA = 2
GENERIC_CAPABILITY_TOKENS = {
    "a",
    "an",
    "the",
    "allow",
    "build",
    "create",
    "coordinate",
    "customer",
    "customers",
    "develop",
    "deliver",
    "enable",
    "expose",
    "facilitate",
    "handle",
    "implement",
    "manage",
    "organize",
    "provide",
    "receive",
    "resident",
    "residents",
    "shared",
    "support",
    "system",
    "user",
    "users",
}
NON_ENFORCEABLE_GOAL_VERBS = {"build", "develop", "organize"}
GENERIC_WORK_TITLE_TOKENS = {
    "add",
    "build",
    "configure",
    "create",
    "deliver",
    "enable",
    "implement",
    "set",
    "setup",
    "support",
}


def normalize_title(title: str) -> str:
    """Normalize superficial title differences before comparing ticket intent."""
    words = re.findall(r"[a-z0-9]+", title.casefold())
    return " ".join(words)


def normalized_tokens(text: str) -> set[str]:
    return set(re.findall(r"[a-z0-9]+", text.casefold()))


def word_forms(word: str) -> set[str]:
    base = word[:-1] if word.endswith("s") and not word.endswith("ss") else word
    forms = {word, base, f"{base}s"}
    if base.endswith("e"):
        forms.update({f"{base}d", f"{base[:-1]}ing", f"{base[:-1]}ings"})
    else:
        forms.update({f"{base}ed", f"{base}ing", f"{base}ings"})
        if len(base) >= 3 and base[-1] not in "aeiou" and base[-2] in "aeiou":
            forms.update({f"{base}{base[-1]}ed", f"{base}{base[-1]}ing"})
    return forms


def capability_matches_ticket(capability: str, ticket_tokens: set[str]) -> bool:
    ordered_tokens = re.findall(r"[a-z0-9]+", capability.casefold())
    if ordered_tokens and ordered_tokens[0] in NON_ENFORCEABLE_GOAL_VERBS:
        return True
    capability_tokens = set(ordered_tokens) - GENERIC_CAPABILITY_TOKENS
    return not capability_tokens or all(
        word_forms(token) & ticket_tokens for token in capability_tokens
    )


def find_missing_capabilities(draft: ProjectDraft, capabilities: list[str]) -> list[str]:
    ticket_token_sets = [
        normalized_tokens(
            "\n".join((ticket.title, ticket.description, *ticket.acceptance_criteria))
        )
        for ticket in draft.tickets
    ]
    return [
        capability
        for capability in capabilities
        if not any(
            capability_matches_ticket(capability, ticket_tokens)
            for ticket_tokens in ticket_token_sets
        )
    ]


def _selected_ticket_capabilities(context: PlanningContext) -> list[str]:
    selected = next(task for task in context.tasks if task.id == context.selected_task_id)
    return selected.acceptance_criteria or [selected.title]


def _duplicate_existing_work_ids(
    draft: ProjectDraft,
    context: PlanningContext,
) -> list[str]:
    sibling_titles = [
        normalize_title(task.title)
        for task in context.tasks
        if task.id != context.selected_task_id and task.status != "CANCELLED"
    ]
    sibling_intents = [
        normalized_tokens(title) - GENERIC_WORK_TITLE_TOKENS for title in sibling_titles
    ]
    duplicate_ids: list[str] = []
    for ticket in draft.tickets:
        title = normalize_title(ticket.title)
        intent = normalized_tokens(title) - GENERIC_WORK_TITLE_TOKENS
        duplicates_sibling = any(
            title == sibling_title
            or SequenceMatcher(None, title, sibling_title).ratio() >= SIMILARITY_THRESHOLD
            or (
                bool(intent)
                and bool(sibling_intent)
                and len(intent & sibling_intent) / len(intent | sibling_intent) >= 0.75
            )
            for sibling_title, sibling_intent in zip(
                sibling_titles, sibling_intents, strict=True
            )
        )
        if duplicates_sibling:
            duplicate_ids.append(ticket.client_id)
    return duplicate_ids


def evaluate_draft(
    draft: ProjectDraft,
    *,
    context: PlanningContext | None = None,
) -> QualityReport:
    normalized_titles = [normalize_title(ticket.title) for ticket in draft.tickets]
    title_counts = Counter(normalized_titles)
    duplicate_titles = {title for title, count in title_counts.items() if count > 1}
    issues: list[QualityIssue] = []
    deductions = 0

    if duplicate_titles:
        duplicate_ids = [
            ticket.client_id
            for ticket, title in zip(draft.tickets, normalized_titles, strict=True)
            if title in duplicate_titles
        ]
        issues.append(
            QualityIssue(
                code="duplicate_titles",
                message=(
                    "Ticket titles repeat the same normalized action; "
                    "combine or differentiate them."
                ),
                ticket_ids=duplicate_ids,
            )
        )
        deductions += 40

    max_similarity = 0.0
    similar_ids: set[str] = set()
    for (left_index, left), (right_index, right) in combinations(
        enumerate(normalized_titles), 2
    ):
        similarity = SequenceMatcher(None, left, right).ratio()
        max_similarity = max(max_similarity, similarity)
        if left != right and similarity >= SIMILARITY_THRESHOLD:
            similar_ids.add(draft.tickets[left_index].client_id)
            similar_ids.add(draft.tickets[right_index].client_id)

    if similar_ids:
        issues.append(
            QualityIssue(
                code="similar_titles",
                message="Some ticket titles appear to describe nearly the same work.",
                ticket_ids=sorted(similar_ids),
            )
        )
        deductions += 25

    useful_descriptions = [
        ticket
        for ticket in draft.tickets
        if len(ticket.description.strip()) >= MIN_USEFUL_DESCRIPTION_LENGTH
    ]
    thin_description_ids = [
        ticket.client_id for ticket in draft.tickets if ticket not in useful_descriptions
    ]
    if thin_description_ids:
        issues.append(
            QualityIssue(
                code="thin_descriptions",
                message=(
                    "Ticket descriptions need enough implementation context to be actionable."
                ),
                ticket_ids=thin_description_ids,
            )
        )
        deductions += min(30, 10 * len(thin_description_ids))

    useful_criteria = [
        ticket
        for ticket in draft.tickets
        if len(ticket.acceptance_criteria) >= MIN_USEFUL_CRITERIA
    ]
    thin_criteria_ids = [
        ticket.client_id for ticket in draft.tickets if ticket not in useful_criteria
    ]
    if thin_criteria_ids:
        issues.append(
            QualityIssue(
                code="thin_acceptance_criteria",
                message="Tickets need at least two concrete acceptance criteria.",
                ticket_ids=thin_criteria_ids,
            )
        )
        deductions += min(30, 10 * len(thin_criteria_ids))

    if context is not None:
        missing_selected_capabilities = find_missing_capabilities(
            draft, _selected_ticket_capabilities(context)
        )
        if missing_selected_capabilities:
            preview = "; ".join(missing_selected_capabilities[:3])
            issues.append(
                QualityIssue(
                    code="selected_ticket_drift",
                    message=(
                        "The child plan does not implement the selected ticket: "
                        f"{preview}."
                    ),
                )
            )
            deductions += 40

        duplicate_existing_ids = _duplicate_existing_work_ids(draft, context)
        if duplicate_existing_ids:
            issues.append(
                QualityIssue(
                    code="duplicates_existing_work",
                    message=(
                        "Some proposed tickets duplicate non-selected work already in the project."
                    ),
                    ticket_ids=duplicate_existing_ids,
                )
            )
            deductions += 35

    ticket_count = len(draft.tickets)
    metrics = QualityMetrics(
        ticket_count=ticket_count,
        unique_title_ratio=len(set(normalized_titles)) / ticket_count,
        max_title_similarity=max_similarity,
        description_coverage=len(useful_descriptions) / ticket_count,
        acceptance_criteria_coverage=len(useful_criteria) / ticket_count,
    )
    score = max(0, 100 - deductions)
    return QualityReport(
        score=score,
        passed=score >= PASSING_SCORE and not issues,
        issues=issues,
        metrics=metrics,
    )
