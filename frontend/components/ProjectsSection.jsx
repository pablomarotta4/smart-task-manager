const formatDate = (value) => {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Date unavailable";
  return new Intl.DateTimeFormat("en", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
};

const formatLabel = (value) => value?.toLowerCase().replaceAll("_", " ") ?? "not set";

const formatHours = (value) => {
  if (value === null || value === undefined) return "Not estimated";
  return `${Number(value)} hours`;
};

export default function ProjectsSection({
  projects,
  selectedProject,
  tasks,
  phase,
  error,
  onSelectProject,
  onRetry,
}) {
  const loadingProjects = phase === "loading-projects";
  const loadingTasks = phase === "loading-tasks";

  return (
    <section className="projects-stage" aria-labelledby="projects-title">
      <header className="projects-hero">
        <p className="section-index">02 / Project archive</p>
        <h1 id="projects-title">Your projects</h1>
        <p className="muted-copy">
          Open a saved brief to inspect its ordered backlog, quality context, and next moves.
        </p>
      </header>

      {error ? (
        <div className="projects-error" role="alert">
          <p>{error}</p>
          <button className="text-action" type="button" onClick={onRetry}>Try again</button>
        </div>
      ) : null}

      {loadingProjects ? (
        <div className="archive-status" role="status" aria-live="polite">
          <span className="status-orbit" aria-hidden="true" />
          <p>Opening the project archive…</p>
        </div>
      ) : null}

      {!loadingProjects && projects.length === 0 && !error ? (
        <div className="projects-empty">
          <span aria-hidden="true">∅</span>
          <h2>No projects yet</h2>
          <p>Return to the workshop and confirm a plan to start the archive.</p>
        </div>
      ) : null}

      {projects.length > 0 ? (
        <div className="projects-browser">
          <aside className="project-index" aria-label="Saved projects">
            <p className="index-caption">Newest first · {projects.length} total</p>
            <div className="project-folios">
              {projects.map((project, index) => (
                <button
                  className={`project-folio ${selectedProject?.id === project.id ? "is-selected" : ""}`}
                  type="button"
                  key={project.id}
                  aria-pressed={selectedProject?.id === project.id}
                  onClick={() => onSelectProject(project)}
                >
                  <span className="folio-number">{String(index + 1).padStart(2, "0")}</span>
                  <span className="folio-copy">
                    <strong>{project.name}</strong>
                    <span>{project.objective || "No objective recorded"}</span>
                  </span>
                  <span className="folio-meta">
                    <span>{project.taskCount} tickets</span>
                    <span>{formatDate(project.createdAt)}</span>
                  </span>
                </button>
              ))}
            </div>
          </aside>

          <div className="project-reading-pane">
            {loadingTasks ? (
              <div className="archive-status" role="status" aria-live="polite">
                <span className="status-orbit" aria-hidden="true" />
                <p>Reading the selected backlog…</p>
              </div>
            ) : null}

            {!selectedProject && !loadingTasks ? (
              <div className="project-placeholder">
                <span aria-hidden="true">↳</span>
                <h2>Select a project</h2>
                <p>Choose a folio to read its ticket sequence.</p>
              </div>
            ) : null}

            {selectedProject && !loadingTasks ? (
              <article className="project-detail" aria-labelledby="selected-project-title">
                <header className="project-detail-header">
                  <div>
                    <p className="section-index">Project #{selectedProject.id}</p>
                    <h2 id="selected-project-title">{selectedProject.name}</h2>
                    <p>{selectedProject.objective || "No objective recorded"}</p>
                  </div>
                  <dl>
                    <div><dt>Owner</dt><dd>{selectedProject.ownerUsername}</dd></div>
                    <div><dt>Created</dt><dd>{formatDate(selectedProject.createdAt)}</dd></div>
                    <div><dt>Tickets</dt><dd>{selectedProject.taskCount}</dd></div>
                  </dl>
                </header>

                {tasks.length === 0 ? (
                  <div className="backlog-empty">
                    <h3>No tickets in this project</h3>
                    <p>The project exists, but its backlog is empty.</p>
                  </div>
                ) : (
                  <div className="saved-ticket-list">
                    {tasks.map((task, index) => (
                      <article
                        className="saved-ticket"
                        key={task.id}
                        aria-labelledby={`saved-ticket-${task.id}`}
                      >
                        <header>
                          <span className="saved-ticket-number">
                            {String(index + 1).padStart(2, "0")}
                          </span>
                          <div>
                            <p>{formatLabel(task.status)} · {formatLabel(task.priority)}</p>
                            <h3 id={`saved-ticket-${task.id}`}>{task.title}</h3>
                          </div>
                        </header>
                        <p className="saved-ticket-description">{task.description}</p>
                        <dl className="saved-ticket-meta">
                          <div><dt>Category</dt><dd>{task.category || "Not set"}</dd></div>
                          <div><dt>Estimate</dt><dd>{formatHours(task.estimatedHours)}</dd></div>
                          <div><dt>Due</dt><dd>{formatDate(task.dueDate)}</dd></div>
                        </dl>
                        {task.aiSummary ? <p className="saved-ai-note">{task.aiSummary}</p> : null}
                        {task.acceptanceCriteria?.length > 0 ? (
                          <div className="saved-criteria">
                            <h4>Acceptance criteria</h4>
                            <ul>
                              {task.acceptanceCriteria.map((criterion) => (
                                <li key={criterion}>{criterion}</li>
                              ))}
                            </ul>
                          </div>
                        ) : null}
                        <div className="saved-dependencies">
                          <strong>Depends on</strong>
                          {task.dependsOn?.length > 0 ? (
                            <ul>
                              {task.dependsOn.map((dependency) => (
                                <li key={dependency}>{dependency}</li>
                              ))}
                            </ul>
                          ) : (
                            <span>Starts the sequence</span>
                          )}
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </article>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  );
}
