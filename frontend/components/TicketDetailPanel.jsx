import { useEffect, useRef, useState } from "react";

const STATUSES = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const labelFor = (value) => value.toLowerCase().replaceAll("_", " ");

export default function TicketDetailPanel({
  task,
  saving,
  error,
  onClose,
  onSave,
}) {
  const closeButtonRef = useRef(null);
  const [draft, setDraft] = useState({
    title: task.title,
    description: task.description ?? "",
    status: task.status,
    priority: task.priority ?? "MEDIUM",
    dueDate: task.dueDate ?? "",
  });

  useEffect(() => {
    const previouslyFocused = document.activeElement;
    const handleKeyDown = (event) => {
      if (event.key === "Escape") onClose();
    };

    closeButtonRef.current?.focus();
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previouslyFocused?.focus();
    };
  }, [onClose]);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setDraft((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    onSave(task, {
      title: draft.title.trim(),
      description: draft.description.trim(),
      status: draft.status,
      projectId: task.projectId,
      assigneeId: task.assigneeId ?? null,
      priority: draft.priority,
      category: task.category ?? null,
      dueDate: draft.dueDate || null,
      position: task.position,
    });
  };

  return (
    <div className="ticket-sheet-backdrop">
      <aside
        className="ticket-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={`edit-ticket-${task.id}`}
      >
        <header className="ticket-sheet-header">
          <div>
            <p className="section-index">Ticket #{task.id}</p>
            <h2 id={`edit-ticket-${task.id}`}>Edit {task.title}</h2>
          </div>
          <button
            ref={closeButtonRef}
            className="sheet-close"
            type="button"
            onClick={onClose}
            aria-label="Close ticket"
          >
            ×
          </button>
        </header>

        <form className="ticket-sheet-form" onSubmit={handleSubmit}>
          <div className="field-group">
            <label htmlFor={`ticket-title-${task.id}`}>Title</label>
            <input
              id={`ticket-title-${task.id}`}
              name="title"
              value={draft.title}
              onChange={handleChange}
              maxLength={255}
              required
            />
          </div>

          <div className="field-group">
            <label htmlFor={`ticket-description-${task.id}`}>Description</label>
            <textarea
              id={`ticket-description-${task.id}`}
              name="description"
              value={draft.description}
              onChange={handleChange}
              rows={5}
            />
          </div>

          <div className="ticket-sheet-fields">
            <div className="field-group">
              <label htmlFor={`ticket-status-${task.id}`}>Status</label>
              <select
                id={`ticket-status-${task.id}`}
                name="status"
                value={draft.status}
                onChange={handleChange}
              >
                {STATUSES.map((status) => (
                  <option key={status} value={status}>{labelFor(status)}</option>
                ))}
              </select>
            </div>
            <div className="field-group">
              <label htmlFor={`ticket-priority-${task.id}`}>Priority</label>
              <select
                id={`ticket-priority-${task.id}`}
                name="priority"
                value={draft.priority}
                onChange={handleChange}
              >
                {PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>{labelFor(priority)}</option>
                ))}
              </select>
            </div>
            <div className="field-group">
              <label htmlFor={`ticket-due-${task.id}`}>Due date</label>
              <input
                id={`ticket-due-${task.id}`}
                name="dueDate"
                type="date"
                value={draft.dueDate}
                onChange={handleChange}
              />
            </div>
          </div>

          <div className="ticket-context-strip">
            <span>{task.category || "Uncategorized"}</span>
            <span>{task.estimatedHours == null ? "No estimate" : `${Number(task.estimatedHours)}h estimate`}</span>
            <span>{task.dependsOn?.length ? `${task.dependsOn.length} dependencies` : "No dependencies"}</span>
          </div>

          {task.acceptanceCriteria?.length ? (
            <section className="sheet-criteria" aria-labelledby={`criteria-${task.id}`}>
              <h3 id={`criteria-${task.id}`}>Acceptance criteria</h3>
              <ul>
                {task.acceptanceCriteria.map((criterion) => (
                  <li key={criterion}>{criterion}</li>
                ))}
              </ul>
            </section>
          ) : null}

          {error ? <p className="error-banner" role="alert">{error}</p> : null}

          <footer className="ticket-sheet-actions">
            <button className="text-action" type="button" onClick={onClose}>Cancel</button>
            <button
              className="primary-action"
              type="submit"
              disabled={saving || !draft.title.trim()}
              aria-busy={saving}
            >
              <span>{saving ? "Saving…" : "Save ticket"}</span>
              <span aria-hidden="true">↗</span>
            </button>
          </footer>
        </form>
      </aside>
    </div>
  );
}
