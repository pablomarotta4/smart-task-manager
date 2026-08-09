from __future__ import annotations

from smart_task_ai.contracts import Priority, ProjectDraft, TicketDraft
from smart_task_ai.quality import evaluate_draft, normalize_title


def ticket(client_id: str, title: str, *, description: str | None = None) -> TicketDraft:
    return TicketDraft(
        client_id=client_id,
        title=title,
        description=description
        or (
            f"Implement {title.lower()} as a complete vertical slice, including validation, "
            "error handling, and automated coverage."
        ),
        priority=Priority.MEDIUM,
        estimated_hours=4,
        acceptance_criteria=[
            f"A user can complete {title.lower()} successfully",
            f"Automated tests cover failures in {title.lower()}",
        ],
    )


def draft(tickets: list[TicketDraft]) -> ProjectDraft:
    return ProjectDraft(
        name="Household Budget App",
        objective="Give a household a clear way to record expenses and review its monthly budget.",
        assumptions=["One household uses the first release"],
        risks=["Imported receipt data may be incomplete"],
        tickets=tickets,
    )


def adequate_draft() -> ProjectDraft:
    return draft(
        [
            ticket("setup", "Set up secure household accounts"),
            ticket("expense", "Record and categorize an expense"),
            ticket("budget", "Configure monthly category budgets"),
            ticket("dashboard", "Display spending against the budget"),
        ]
    )


def test_adequate_plan_passes_with_transparent_metrics() -> None:
    report = evaluate_draft(adequate_draft())

    assert report.passed is True
    assert report.score >= 75
    assert report.issues == []
    assert report.metrics.ticket_count == 4
    assert report.metrics.unique_title_ratio == 1
    assert report.metrics.description_coverage == 1
    assert report.metrics.acceptance_criteria_coverage == 1


def test_repeated_normalized_titles_are_flagged() -> None:
    plan = adequate_draft()
    plan.tickets[1].title = "  RECORD   and categorize an expense!!! "
    plan.tickets[2].title = "Record and categorize an expense"

    report = evaluate_draft(plan)

    assert "duplicate_titles" in {issue.code for issue in report.issues}
    assert report.metrics.unique_title_ratio < 1
    assert report.passed is False


def test_highly_similar_titles_are_flagged_even_when_not_identical() -> None:
    plan = adequate_draft()
    plan.tickets[0].title = "Implement secure user login page"
    plan.tickets[1].title = "Implement secure user login screen"

    report = evaluate_draft(plan)

    assert "similar_titles" in {issue.code for issue in report.issues}
    assert report.metrics.max_title_similarity >= 0.82


def test_thin_descriptions_and_acceptance_criteria_reduce_sufficiency() -> None:
    plan = adequate_draft()
    plan.tickets[0].description = "Create the login behavior and tests."
    plan.tickets[1].description = "Create the expense behavior and tests."
    plan.tickets[0].acceptance_criteria = ["The requested behavior works"]
    plan.tickets[1].acceptance_criteria = ["The requested behavior works"]

    report = evaluate_draft(plan)

    codes = {issue.code for issue in report.issues}
    assert {"thin_descriptions", "thin_acceptance_criteria"} <= codes
    assert report.metrics.description_coverage == 0.5
    assert report.metrics.acceptance_criteria_coverage == 0.5
    assert report.passed is False


def test_title_normalization_ignores_case_punctuation_and_spacing() -> None:
    assert normalize_title("  Build THE API!!! ") == normalize_title("build the api")
