const STATUS_LABELS = {
  PROCESSING: "Processing",
  DRAFT_READY: "Draft ready",
  FAILED: "Needs retry",
  CONFIRMED: "Confirmed",
};

const formatUpdatedAt = (value) => {
  if (!value) return "Time unavailable";
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
};

export default function RecentPlanningRuns({
  runs,
  phase,
  error,
  busyRunId,
  onResume,
  onRetry,
  onOpen,
  onRefresh,
}) {
  return (
    <section className="recent-runs" aria-labelledby="recent-runs-title" aria-busy={phase === "loading"}>
      <header className="recent-runs-heading">
        <div>
          <p className="section-index">Saved in Spring</p>
          <h2 id="recent-runs-title">Recent AI plans</h2>
          <p>Resume a draft or retry interrupted planning without recreating its context.</p>
        </div>
        <button
          className="text-action"
          type="button"
          disabled={phase === "loading"}
          onClick={onRefresh}
        >
          Refresh
        </button>
      </header>

      {phase === "loading" ? <p className="recent-runs-status" role="status">Loading saved plans…</p> : null}
      {error ? (
        <div className="projects-error" role="alert">
          <p>{error}</p>
          <button className="text-action" type="button" onClick={onRefresh}>Try again</button>
        </div>
      ) : null}
      {phase !== "loading" && !error && runs.length === 0 ? (
        <p className="recent-runs-empty">No saved AI plans yet. Your generated drafts will appear here.</p>
      ) : null}

      {runs.length > 0 ? (
        <div className="recent-runs-grid">
          {runs.map((run) => {
            const busy = busyRunId === run.runId;
            const existingTask = run.mode === "EXISTING_TASK";
            return (
              <article className="recent-run-card" key={run.runId}>
                <div className="recent-run-meta">
                  <span>{STATUS_LABELS[run.status] ?? run.status}</span>
                  <span>Attempt {run.attemptCount}</span>
                </div>
                <h3>{existingTask ? run.targetTaskTitle : "New project plan"}</h3>
                <p className="recent-run-context">
                  {existingTask ? run.projectName : run.prompt}
                </p>
                <p className="recent-run-time">Updated {formatUpdatedAt(run.updatedAt)}</p>
                {run.errorCode ? (
                  <p className="recent-run-error">The last planning attempt did not complete.</p>
                ) : null}
                <div className="recent-run-actions">
                  {run.status === "DRAFT_READY" ? (
                    <button
                      className="primary-action compact-action"
                      type="button"
                      disabled={busy}
                      onClick={() => onResume(run)}
                    >
                      <span>{busy ? "Restoring…" : "Resume draft"}</span>
                      <span aria-hidden="true">↗</span>
                    </button>
                  ) : null}
                  {run.retryable ? (
                    <button
                      className="primary-action compact-action"
                      type="button"
                      disabled={busy}
                      onClick={() => onRetry(run)}
                    >
                      <span>{busy ? "Retrying…" : "Retry plan"}</span>
                      <span aria-hidden="true">↻</span>
                    </button>
                  ) : null}
                  {run.status === "CONFIRMED" && run.projectId != null ? (
                    <button className="text-action" type="button" onClick={() => onOpen(run)}>
                      {existingTask ? "Open board" : "View project"}
                    </button>
                  ) : null}
                  {run.status === "PROCESSING" && !run.retryable ? (
                    <span className="recent-run-waiting">Planning is still in progress</span>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}
