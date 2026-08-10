# Execution Workspace Frontend Design

## Product direction

The current frontend creates AI-planned projects and reads their saved backlogs. The next layer should help a person execute that work without turning the application into a generic administration dashboard. The authenticated workspace will keep the existing editorial paper-and-ink language and add three top-level destinations: **Board**, **My Work**, and **Account**. Ticket editing, project settings, and AI follow-up planning will be contextual surfaces inside those destinations rather than permanent navigation items.

## Views

- **Board** selects one saved project and arranges its tickets into Todo, In progress, Blocked, and Done lanes. Selecting a ticket opens a focused editor for the fields the current Spring API can update: title, description, status, priority, and due date.
- **My Work** aggregates open tickets from every saved project, emphasizes overdue and blocked work, and retains project provenance on every card.
- **Account** shows the authenticated identity, workspace access, and implemented product capabilities, with a clear sign-out action.
- **Project desk** inside Board shows the objective, counts, an AI follow-up action, and project-settings boundaries. Since Spring does not yet expose project update/delete or existing-project planning endpoints, those controls explain the limitation instead of simulating persistence.

## Data and interaction

App remains the authenticated orchestration boundary. Board loads project summaries and only the selected backlog. My Work loads summaries first, then project backlogs concurrently. Ticket saves use the existing full task update endpoint and merge only operational fields into the richer planning response so acceptance criteria, dependencies, estimates, and AI context remain visible. A 401 clears the session; other failures remain in the active view with retry paths.

## Quality bar

All destinations expose selected navigation state, loading/error/empty states, keyboard-operable controls, labeled forms, and responsive layouts. The board becomes horizontally scrollable on compact screens rather than crushing ticket cards. Automated tests cover HTTP contracts, navigation, aggregation, editing, and failure truthfulness; a real-browser pass verifies desktop and mobile layouts.
