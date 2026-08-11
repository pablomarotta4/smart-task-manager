import TicketDetailPanel from "./TicketDetailPanel";

const formatDate = (value) => {
  if (!value) return "No due date";
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "2-digit",
    year: "numeric",
  }).format(new Date(year, month - 1, day));
};

const localIsoDate = () => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
};

function WorkTicket({ task, index, onSelectTask }) {
  const overdue = task.dueDate && task.dueDate < localIsoDate();

  return (
    <button
      className={`work-ticket${overdue ? " is-overdue" : ""}`}
      type="button"
      onClick={() => onSelectTask(task)}
      aria-label={`Open ${task.title}`}
    >
      <span className="work-ticket-number">{String(index + 1).padStart(2, "0")}</span>
      <span className="work-ticket-main">
        <span className="work-ticket-project">{task.projectName}</span>
        <strong>{task.title}</strong>
        <span>{task.description}</span>
      </span>
      <span className="work-ticket-meta">
        {overdue ? <span className="work-ticket-overdue">Overdue</span> : null}
        <span>{task.priority?.toLowerCase() ?? "no priority"}</span>
        <span>{task.assigneeUsername ? `Assigned to ${task.assigneeUsername}` : "Assigned to you"}</span>
        <span>{formatDate(task.dueDate)}</span>
      </span>
    </button>
  );
}

export default function MyWorkSection({
  items,
  phase,
  error,
  selectedTask,
  savingTask,
  taskError,
  onSelectTask,
  onCloseTask,
  onSaveTask,
  onRetry,
}) {
  const loading = phase === "loading-projects" || phase === "loading-tasks";
  const openItems = items.filter(
    (task) => task.status !== "DONE" && task.status !== "CANCELLED",
  );
  const blocked = openItems.filter((task) => task.status === "BLOCKED");
  const focus = openItems.filter(
    (task) => task.status !== "BLOCKED"
      && (task.status === "IN_PROGRESS" || ["HIGH", "URGENT"].includes(task.priority)),
  );
  const focusIds = new Set(focus.map((task) => task.id));
  const dueNext = openItems
    .filter((task) => task.status !== "BLOCKED" && !focusIds.has(task.id))
    .toSorted((left, right) => {
      if (!left.dueDate) return right.dueDate ? 1 : 0;
      if (!right.dueDate) return -1;
      return left.dueDate.localeCompare(right.dueDate);
    });
  const completed = items.filter((task) => task.status === "DONE").length;

  return (
    <section className="my-work-stage" aria-labelledby="my-work-title" aria-busy={loading}>
      <header className="my-work-hero">
        <div>
          <p className="section-index">04 / Personal queue</p>
          <h1 id="my-work-title">My work</h1>
        </div>
        <p>
          Every ticket assigned to you, across every project. Start with motion, then clear the blockers.
        </p>
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
          <p>Collecting your assigned work…</p>
        </div>
      ) : null}

      {!loading && !error ? (
        <>
          <section className="work-vitals" aria-label="Work summary">
            <div><strong>{openItems.length}</strong><span>open</span></div>
            <div><strong>{focus.length}</strong><span>in focus</span></div>
            <div><strong>{blocked.length}</strong><span>blocked</span></div>
            <div><strong>{completed}</strong><span>complete</span></div>
          </section>

          {items.length === 0 ? (
            <div className="projects-empty">
              <span aria-hidden="true">∅</span>
              <h2>No assigned work</h2>
              <p>No tickets are assigned to you.</p>
            </div>
          ) : (
            <div className="work-ledger">
              <section className="work-ledger-section work-focus" aria-labelledby="focus-title">
                <header>
                  <p>Do now</p>
                  <h2 id="focus-title">Focus</h2>
                  <span>{focus.length}</span>
                </header>
                <div>
                  {focus.length ? focus.map((task, index) => (
                    <WorkTicket key={task.id} task={task} index={index} onSelectTask={onSelectTask} />
                  )) : <p className="work-empty-line">No high-priority work is waiting.</p>}
                </div>
              </section>

              <section className="work-ledger-section work-blocked" aria-labelledby="blocked-title">
                <header>
                  <p>Unstick</p>
                  <h2 id="blocked-title">Blocked</h2>
                  <span>{blocked.length}</span>
                </header>
                <div>
                  {blocked.length ? blocked.map((task, index) => (
                    <WorkTicket key={task.id} task={task} index={index} onSelectTask={onSelectTask} />
                  )) : <p className="work-empty-line">Nothing is blocked.</p>}
                </div>
              </section>

              <section className="work-ledger-section work-due" aria-labelledby="due-title">
                <header>
                  <p>Sequence</p>
                  <h2 id="due-title">Due next</h2>
                  <span>{dueNext.length}</span>
                </header>
                <div>
                  {dueNext.length ? dueNext.map((task, index) => (
                    <WorkTicket key={task.id} task={task} index={index} onSelectTask={onSelectTask} />
                  )) : <p className="work-empty-line">No remaining work is waiting.</p>}
                </div>
              </section>
            </div>
          )}
        </>
      ) : null}

      {selectedTask ? (
        <TicketDetailPanel
          key={selectedTask.id}
          task={selectedTask}
          canManage={false}
          saving={savingTask}
          error={taskError}
          onClose={onCloseTask}
          onSave={onSaveTask}
        />
      ) : null}
    </section>
  );
}
