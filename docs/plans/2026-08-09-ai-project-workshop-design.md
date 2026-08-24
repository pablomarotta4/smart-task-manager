# AI Project Workshop Frontend Design

## Product scope

The frontend is a focused local workspace for the project-generation flow that already exists in Spring. A user signs in, describes a project in ordinary language, reviews the generated draft and its quality report, edits the project and ticket content, and explicitly confirms it. Confirmation is the only action that creates database records. The interface will not add project browsing, task management, registration, model configuration, or administration; those belong to later product slices.

The primary success path is: login, enter a prompt, wait with honest progress messaging, inspect a readable project outline, edit any weak wording, and confirm. A low AI quality score remains visible and does not disappear behind reassuring copy. The user can still edit and confirm a structurally valid draft because the existing backend treats the quality result as advice, not an authorization decision.

## Technical approach

Use a React 19 single-page application built with Vite and served on port 3000. The frontend lives under `frontend/` while the existing root `package.json` owns scripts and dependencies. A small fetch client centralizes the Spring base URL, JSON parsing, bearer authentication, and error normalization. The JWT stays in `sessionStorage`, so refreshing the tab preserves the local test session without creating a long-lived credential.

The application uses explicit view states rather than a routing library: signed out, prompt entry, generating, draft review, confirming, and confirmed. This keeps the MVP small and makes invalid transitions difficult. React state owns an editable copy of the returned draft; the original `runId`, quality report, revision count, and model metadata remain immutable. Confirmation sends exactly `{ "draft": editedDraft }` to the run-specific endpoint.

## Visual system and interaction

The visual direction is an editorial project workshop: warm paper, near-black ink, vermilion actions, olive quality accents, serif display typography, and compact technical labels. A ruled-paper grid and offset shadows make the draft feel like a working plan rather than a generic SaaS dashboard. Ticket cards are numbered and connected by a vertical planning line. Quality metrics are presented as evidence, with issue text shown prominently when the gate does not pass.

Every field has a visible label, keyboard focus is obvious, loading states use text as well as motion, and status changes use live regions. The layout collapses from a two-column prompt/draft composition to a single column on narrow screens. Motion is limited to the initial reveal, progress pulse, and card entrance, and respects reduced-motion preferences.

## Error handling and verification

The client extracts Spring's `message`, `details`, or validation payload when available and falls back to a concise status-based message. A 401 clears the local session and returns to login. Network and provider failures keep the user's prompt intact so they can retry. Buttons are disabled during requests to prevent accidental duplicate actions; backend idempotency remains the final protection for confirmation.

Vitest and React Testing Library cover API serialization, authentication, generation, editing, confirmation, and error recovery. A production Vite build verifies bundling. Playwright then exercises the running UI against the real local Spring, FastAPI, Ollama, and PostgreSQL stack at desktop and mobile widths, including a screenshot and browser-console check.
