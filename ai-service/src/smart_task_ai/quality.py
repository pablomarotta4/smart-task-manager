from __future__ import annotations

import re
from collections import Counter
from difflib import SequenceMatcher
from itertools import combinations

from smart_task_ai.contracts import (
    ProjectDraft,
    QualityIssue,
    QualityMetrics,
    QualityReport,
)

PASSING_SCORE = 75
SIMILARITY_THRESHOLD = 0.82
MIN_USEFUL_DESCRIPTION_LENGTH = 60
MIN_USEFUL_CRITERIA = 2


def normalize_title(title: str) -> str:
    """Normalize superficial title differences before comparing ticket intent."""
    words = re.findall(r"[a-z0-9]+", title.casefold())
    return " ".join(words)


def evaluate_draft(draft: ProjectDraft) -> QualityReport:
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
