# Project Browser Design

## Purpose and scope

The project workshop can create a persisted project and backlog, but it currently has no way to browse that work. Add a read-only Projects section for authenticated users. The section lists saved projects newest first and opens one project's ticket sequence without leaving the application. It does not edit or delete projects or tickets; those interactions remain future work.

The primary navigation has two explicit views: **Workshop** and **Projects**. The Projects view preserves the existing editorial workshop aesthetic: a large index heading, compact project folios, warm paper surfaces, ink borders, and vermilion highlights. Each project card shows its name, owner, creation date, objective, and ticket count. Selecting a card loads its tickets into a detail area. Ticket entries show status, priority, category, due date, estimate, description, acceptance criteria, AI summary, and dependencies when the data exists. Mobile layouts stack the index and details without horizontal scrolling.

## Architecture and data flow

Spring remains the source of truth. `GET /api/projects` continues to return project responses, extended additively with objective and task count. Its service method uses a read-only transaction so lazy owner fields are mapped before the persistence session closes; this repairs the observed HTTP 500 without changing the route. `GET /api/tasks/project/{projectId}` remains the ticket route and returns additive planning details. Criteria and dependencies are fetched for the selected project in bounded project-level queries rather than one query per ticket.

React adds `getProjects` and `getProjectTasks` to the existing API client. Entering Projects loads summaries once. Selecting a project loads only that backlog, with explicit loading, empty, and error states. The creation receipt links directly to its newly created project. Authentication failures clear the browser session consistently with generation and confirmation. Tests cover the backend lazy-loading regression, enriched ticket contract, frontend API calls, navigation, project selection, empty/error states, and post-confirmation handoff.

