# Product Capabilities Epic Design

**Date:** 2026-08-12

**Status:** Approved working design based on three independent repository design reviews

## Goal

Evolve Smart Task Manager from a single-owner execution workspace into a secure collaborative product with project roles, invitations, comments, activity history, notifications, discovery, account recovery, recurring work, templates, attachments, reports, and a coherent AI boundary.

The finished product must remain easy to run locally, must not require external infrastructure for its core workflows, and must preserve the existing explicit AI draft-to-confirmation boundary.

## Architectural choice

Use an incremental modular monolith:

- React owns presentation and client-side interaction only.
- Spring Boot owns authentication, authorization, business rules, scheduling, transactions, persistence, and every authoritative write.
- PostgreSQL is the system of record and supplies paginated search and aggregate reporting.
- FastAPI is the single model-provider boundary and produces bounded, structured, editable or read-only AI output.
- Email and file storage are behind replaceable adapters. Local development uses Mailpit and a configured local attachment directory.

Rejected alternatives:

- A metadata-first shortcut based on generic JSON, client-side filtering, global labels, and database attachment blobs would be faster initially but would weaken authorization, referential integrity, search, and reporting.
- A microservice/event-bus/search-cluster architecture would add eventual consistency and operational failure modes before the product has evidence it needs that scale.
- A general autonomous AI project manager would make permissions, auditability, prompt injection, concurrency, and cost substantially harder without improving the deterministic product features in this epic.

## Shared foundations

### Project authorization

Keep the global `USER`/`ADMIN` role separate from project-scoped roles:

- `OWNER`: full project control; exactly one per project.
- `MANAGER`: manage ordinary members, tickets, assignments, labels, schedules, and AI planning.
- `MEMBER`: view the project, work assigned tickets, and participate in comments.

`projects.owner_id` remains the compatibility owner reference. Every project also has an owner membership. A centralized `ProjectAccessPolicy` authorizes every project-aware service, including AI context, planning confirmation, task queries, attachments, reports, and notifications. Project roles are read from PostgreSQL for every operation and are never trusted from JWT claims or frontend state.

Nonmembers receive `404` for project-scoped resources to prevent enumeration. Authenticated members who lack a capability receive `403`. Ownership transfer is outside this epic; the owner cannot be removed or demoted.

### Business activity and delivery

Business mutations write their entity changes, immutable activity record, and in-app notifications in one transaction. Activity records use typed, whitelisted payloads and never contain comment bodies, prompts, secrets, tokens, or arbitrary serialized DTOs.

Outbound email uses an `email_outbox`. Business transactions enqueue an aggregate reference; a bounded dispatcher signs action links while sending and retries failures outside the transaction. In-app notifications remain transactional and immediately consistent. No Kafka, Redis, or external queue is required.

### Safe tests and observability

All database integration tests use disposable Testcontainers PostgreSQL. Tests must refuse to run destructive cleanup against a non-container datasource. Flyway is tested from an empty database and sequentially through every migration.

Every request carries a correlation ID through Spring and FastAPI. Metrics use bounded labels and cover authorization denials, invitation outcomes, outbox age, notification delivery, search latency, recurrence results, attachment failures, and AI call outcomes. Logs never contain personal message content, email addresses, search text, attachment names, or bearer/action tokens.

## Collaboration and identity

### Invitations and memberships

Invitations target a normalized email and a stored `MANAGER` or `MEMBER` role. They have an explicit `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, or `EXPIRED` state and a bounded expiry. The raw token is never stored.

Action links place tokens in the URL fragment, for example `/invite#token=...`. React submits the token in a POST body, preventing it from leaking through access logs and referrer headers. Acceptance requires an authenticated account with a matching verified normalized email, locks the invitation row, and is idempotent under concurrent requests. A client can never choose the accepted role.

### Account actions

Existing accounts are backfilled as verified. New registrations can use personal project features immediately, but collaboration actions require email verification. Verification and password reset use signed, versioned, single-use action records with the same no-raw-token rule as invitations.

Forgot-password and resend-verification requests always return the same `202` response regardless of account existence. A successful reset revokes all refresh tokens and increments an authentication version so active access tokens become invalid. Usernames become immutable because the JWT subject currently uses the username.

Login, registration, recovery requests, and token confirmations use bounded in-memory rate-limit adapters with a fake clock in tests. Rate limiting happens before password hashing, authentication, or email/database side effects. A future multi-replica deployment can replace the adapter with Redis without changing controllers.

### Comments and activity

All project members can add comments. Authors can edit or soft-delete their own comments; managers and owners can moderate with an explicit moderation activity. Comment bodies are limited to 5,000 characters.

Ticket activity is append-only and cursor-paginated using `(occurred_at, id)`. It includes creation, deletion, field changes, assignment, status, comments, planning confirmation, membership, labels, recurrence, and attachments. Deleted tasks leave title and ID snapshots so project history remains understandable.

### Notifications

The first notification types are assignment, mention, comment-on-assigned-ticket, due soon, overdue, and invitation accepted. Mentions resolve exact usernames only among current project members. Due notifications use the recipient preference timezone and deterministic deduplication keys so multiple scheduler instances cannot duplicate delivery.

Users have an inbox with read/unread state and preferences for notification type, email channel, due lead days, and timezone. Only the recipient can read or mutate a notification.

## Discovery and work automation

### Labels, search, and filters

Labels are project-scoped with case-insensitive uniqueness inside a project. The existing unused global label schema is migrated safely so two projects can each use `Bug`, while `Bug` and `bug` cannot coexist in one project. Managers maintain the label catalog; any user allowed to edit a ticket may attach existing labels from that ticket's project.

Global search is authorization-scoped, server-side, paginated, and projection-based. Filters include project, status, priority, assignee, label, and due bucket. Filters combine with `AND`, repeated values within one filter use `OR`, page size is capped at 50, and every sort includes task ID as a deterministic tie-breaker. The initial implementation uses PostgreSQL case-insensitive matching and measured indexes rather than a new search service.

### Templates

Project templates contain a versioned immutable blueprint: objective, labels, ticket definitions, acceptance criteria, internal dependency keys, and estimates. They exclude assignees, runtime status, actual dates, attachments, planning IDs, and AI metadata.

A shared `ProjectBlueprintMaterializer` validates the dependency graph and creates a project transactionally. AI confirmation adapts its draft into the same blueprint. Template instantiation takes a client UUID and is idempotent.

### Recurring tickets

A recurrence rule stores an immutable ticket snapshot, cadence, interval, timezone, due-date offset, next execution, optional end, and optional assignee. A unique `(rule_id, scheduled_for)` occurrence record guarantees idempotency.

A scheduled poller claims a bounded batch and commits each rule independently. It creates at most one catch-up occurrence before advancing into the future. Generated tickets never copy attachments, runtime status, completed timestamps, or cross-task dependencies. If the stored assignee is no longer an active project member, the occurrence is unassigned.

### Attachments

PostgreSQL stores attachment metadata only. `AttachmentStorage` provides a local filesystem implementation now and an S3-compatible implementation later. Generated object keys, not user filenames, determine paths.

Every list, upload, download, and delete request re-authorizes through the task and project. Upload validation enforces filename length, size and per-task quota, allowed content type, and file signature. Downloads use `Content-Disposition: attachment`. Temporary storage and metadata finalization avoid holding database transactions open during slow I/O and prevent orphaned objects after failed operations.

## Reports

The first report is explicitly a current-state report: counts by project, status, priority, assignee, and label; open, blocked, overdue, unassigned, and completed-in-range; workload; and cycle time where reliable timestamps exist.

Historical measures such as time in status, reopen rate, throughput trend, and burndown derive from activity history after activity capture exists. Reports use authorized SQL aggregation and bounded date ranges rather than loading tasks into Java or calculating from the currently visible React board. CSV export reuses search filters and neutralizes spreadsheet formulas.

## AI boundary

The legacy Spring `AIController -> AIService -> OllamaService` route is removed or delegated to FastAPI so there is one provider boundary. Existing FastAPI planning remains an explicit user action with bounded context, structured contracts, deterministic quality validation, at most one revision, an editable draft, and Spring-owned confirmation.

No feature in this epic uses AI for permissions, membership, notification eligibility, recurrence dates, search, templates, attachments, account state, or authoritative analytics.

After comments, activity, and reports are stable, two optional bounded workflows may be evaluated separately:

- An evidence-linked, read-only project health brief.
- Draft follow-up tickets from selected comments/activity, with source IDs and explicit confirmation.

Neither workflow may use long-lived memory, model-selected tools, autonomous mutation, unbounded fan-out, or cross-project context.

## Frontend structure

The existing application shell remains the routing/session owner, but new behavior is implemented in focused modules:

- Invitations and project people management.
- Ticket comments and activity tabs.
- Notification inbox and preferences.
- Search and filter state.
- Label catalog and ticket label selection.
- Templates, recurring tickets, attachments, and reports.
- Account verification and recovery routes.

Only one integration owner edits `frontend/App.jsx` and `frontend/styles.css` in a wave. Feature agents own isolated components, hooks, API modules, and tests. Permission-aware controls are usability only; Spring remains authoritative.

## Delivery order

1. Test isolation, CI, correlation IDs, project roles, and centralized authorization.
2. Action-link, email-outbox, verification, recovery, and rate-limit foundations.
3. Invitations and membership management.
4. Activity capture and comments.
5. Notification inbox, preferences, and due scheduler.
6. Project labels, paginated search, and filters.
7. Shared blueprint materializer and project templates.
8. Recurring tickets and attachments.
9. Reports and CSV export.
10. AI-boundary consolidation, end-to-end journeys, security review, performance probes, and issue remediation.

## Completion criteria

- Every endpoint passes the owner/manager/member/nonmember authorization matrix.
- Cross-project IDs never disclose or mutate inaccessible resources.
- Invitation and account tokens are single-use, bounded, unlogged, and never stored raw.
- Business mutations and their activity/in-app notifications are atomic.
- Search is scoped, paginated, deterministic, and filter-composable.
- Recurrence and template instantiation are idempotent under concurrency.
- Attachments cannot escape storage roots, bypass authorization, or orphan objects on tested failure paths.
- Current-state and historical reports make only claims supported by persisted evidence.
- The application remains usable when SMTP, attachment delivery, or AI is unavailable.
- Backend unit/security/integration tests, frontend unit/build tests, AI tests, contract fixtures, and a critical Playwright collaboration journey pass in CI.
- A final security and code-quality review has no unresolved critical or important findings.

