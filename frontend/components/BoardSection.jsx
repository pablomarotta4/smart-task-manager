import TicketDetailPanel from "./TicketDetailPanel";

const LANES = [
  { status: "TODO", label: "Todo", marker: "01" },
  { status: "IN_PROGRESS", label: "In progress", marker: "02" },
  { status: "BLOCKED", label: "Blocked", marker: "03" },
  { status: "DONE", label: "Done", marker: "04" },
];

const priorityLabel = (priority) => priority?.toLowerCase() ?? "not set";

const formatDueDate = (value) => {
  if (!value) return "No due date";
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return new Intl.DateTimeFormat("en", { month: "short", day: "2-digit" }).format(date);
};

export default function BoardSection({
  projects,
  selectedProject,
  tasks,
  phase,
  error,
  selectedTask,
  savingTask,
  taskError,
  onSelectProject,
  onSelectTask,
  onCloseTask,
  onSaveTask,
  onRetry,
}) {
  const loading = phase === "loading-projects" || phase === "loading-tasks";
  const activeTasks = tasks.filter((task) => task.status !== "CANCELLED");
  const completedCount = activeTasks.filter((task) => task.status === "DONE").length;
  const progress = activeTasks.length === 0
    ? 0
    : Math.round((completedCount / activeTasks.length) * 100);

  return (
    <section className="board-stage" aria-labelledby="board-title" aria-busy={loading}>
      <header className="board-hero">
        <div>
          <p className="section-index">03 / Execution desk</p>
          <h1 id="board-title">Project board</h1>
        </div>
        <div className="board-project-control">
          <label htmlFor="board-project">Active project</label>
          <select
            id="board-project"
            value={selectedProject?.id ?? ""}
            onChange={(event) => {
              const project = projects.find(
                (candidate) => String(candidate.id) === event.target.value,
              );
              if (project) onSelectProject(project);
            }}
            disabled={loading || projects.length === 0}
          >
            {projects.length === 0 ? <option value="">No saved projects</option> : null}
            {projects.map((project) => (
              <option key={project.id} value={project.id}>{project.name}</option>
            ))}
          </select>
        </div>
      </header>

      {error ? (
        <div className="projects-error" role="alert">
          <p>{error}</p>
          <button className="text-action" type="button" onClick={onRetry}>Try again</button>
        </div>
      ) : null}

      {loading ? (
        <div className="archive-status" role="status">
          <span className="status-orbit" aria-hidden="true" />
          <p>Arranging the execution desk…</p>
        </div>
      ) : null}

      {!loading && selectedProject && !error ? (
        <>
          <section className="board-project-banner" aria-label="Project progress">
            <div>
              <p>Project #{selectedProject.id}</p>
              <h2>{selectedProject.name}</h2>
              <p>{selectedProject.objective || "No objective recorded"}</p>
            </div>
            <div className="board-progress">
              <strong>{progress}%</strong>
              <span>{completedCount} of {activeTasks.length} complete</span>
              <div className="board-progress-track" aria-hidden="true">
                <span style={{ width: `${progress}%` }} />
              </div>
            </div>
          </section>

          <div className="board-lanes" aria-label={`${selectedProject.name} ticket board`}>
            {LANES.map((lane) => {
              const laneTasks = activeTasks.filter((task) => task.status === lane.status);
              return (
                <section className={`board-lane board-lane-${lane.status.toLowerCase()}`} key={lane.status}>
                  <header>
                    <span>{lane.marker}</span>
                    <h2>{lane.label}</h2>
                    <strong>{laneTasks.length}</strong>
                  </header>
                  <div className="board-ticket-stack">
                    {laneTasks.length === 0 ? (
                      <p className="lane-empty">Clear lane</p>
                    ) : laneTasks.map((task) => (
                      <button
                        className="board-ticket"
                        type="button"
                        key={task.id}
                        onClick={() => onSelectTask(task)}
                        aria-label={`Open ${task.title}`}
                      >
                        <span className="board-ticket-topline">
                          <span>{task.category || "General"}</span>
                          <span>{priorityLabel(task.priority)}</span>
                        </span>
                        <strong>{task.title}</strong>
                        <span className="board-ticket-copy">{task.description}</span>
                        <span className="board-ticket-foot">
                          <span>{task.estimatedHours == null ? "—" : `${Number(task.estimatedHours)}h`}</span>
                          <span>{formatDueDate(task.dueDate)}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        </>
      ) : null}

      {!loading && projects.length === 0 && !error ? (
        <div className="projects-empty">
          <span aria-hidden="true">∅</span>
          <h2>No board yet</h2>
          <p>Create a project in the Workshop to open an execution board.</p>
        </div>
      ) : null}

      {selectedTask ? (
        <TicketDetailPanel
          key={selectedTask.id}
          task={selectedTask}
          saving={savingTask}
          error={taskError}
          onClose={onCloseTask}
          onSave={onSaveTask}
        />
      ) : null}
    </section>
  );
}
