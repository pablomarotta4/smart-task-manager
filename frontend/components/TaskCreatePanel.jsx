import { useState } from "react";

const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const EMPTY_TASK = {
  title: "",
  description: "",
  priority: "MEDIUM",
  category: "",
  dueDate: "",
  assigneeId: "",
};

const labelFor = (value) => value.toLowerCase().replaceAll("_", " ");

export default function TaskCreatePanel({
  project,
  members,
  permissions,
  creating,
  error,
  onCreate,
  onCancel,
}) {
  const [draft, setDraft] = useState(EMPTY_TASK);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setDraft((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const created = await onCreate(project.id, {
      title: draft.title.trim(),
      description: draft.description.trim() || null,
      status: "TODO",
      projectId: project.id,
      assigneeId: permissions.canAssignTask && draft.assigneeId
        ? Number(draft.assigneeId)
        : null,
      priority: draft.priority,
      category: draft.category.trim() || null,
      dueDate: draft.dueDate || null,
      position: null,
    });
    if (created) {
      setDraft(EMPTY_TASK);
      onCancel();
    }
  };

  return (
    <section className="task-create-panel" aria-labelledby="new-ticket-title">
      <header>
        <div>
          <p className="section-index">New ticket · Project #{project.id}</p>
          <h2 id="new-ticket-title">Add a concrete next move</h2>
        </div>
        <button className="text-action" type="button" onClick={onCancel}>Close</button>
      </header>

      <form className="task-create-form" onSubmit={handleSubmit}>
        <div className="field-group task-create-title">
          <label htmlFor="new-ticket-name">Ticket title</label>
          <input
            id="new-ticket-name"
            name="title"
            value={draft.title}
            onChange={handleChange}
            maxLength={255}
            required
          />
        </div>
        <div className="field-group task-create-description">
          <label htmlFor="new-ticket-description">Ticket description</label>
          <textarea
            id="new-ticket-description"
            name="description"
            value={draft.description}
            onChange={handleChange}
            rows={3}
          />
        </div>
        <div className="field-group">
          <label htmlFor="new-ticket-priority">Ticket priority</label>
          <select
            id="new-ticket-priority"
            name="priority"
            value={draft.priority}
            onChange={handleChange}
          >
            {PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>{labelFor(priority)}</option>
            ))}
          </select>
        </div>
        {permissions.canAssignTask ? (
          <div className="field-group">
            <label htmlFor="new-ticket-assignee">Ticket assignee</label>
            <select
              id="new-ticket-assignee"
              name="assigneeId"
              value={draft.assigneeId}
              onChange={handleChange}
            >
              <option value="">Unassigned</option>
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.fullName || member.username}
                </option>
              ))}
            </select>
          </div>
        ) : null}
        <div className="field-group">
          <label htmlFor="new-ticket-category">Ticket category</label>
          <input
            id="new-ticket-category"
            name="category"
            value={draft.category}
            onChange={handleChange}
            maxLength={32}
          />
        </div>
        <div className="field-group">
          <label htmlFor="new-ticket-due">Ticket due date</label>
          <input
            id="new-ticket-due"
            name="dueDate"
            type="date"
            value={draft.dueDate}
            onChange={handleChange}
          />
        </div>
        {error ? <p className="project-mutation-error" role="alert">{error.message}</p> : null}
        <button
          className="primary-action task-create-submit"
          type="submit"
          disabled={creating || draft.title.trim().length === 0}
          aria-busy={creating}
        >
          <span>{creating ? "Creating ticket…" : "Create ticket"}</span>
          <span aria-hidden="true">↗</span>
        </button>
      </form>
    </section>
  );
}
