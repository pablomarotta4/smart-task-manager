const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT"];

export default function TicketEditor({ ticket, index, onChange }) {
  const updateField = (field, value) => {
    onChange({ ...ticket, [field]: value });
  };

  const updateCriterion = (criterionIndex, value) => {
    const acceptanceCriteria = ticket.acceptance_criteria.map((criterion, currentIndex) =>
      currentIndex === criterionIndex ? value : criterion,
    );
    updateField("acceptance_criteria", acceptanceCriteria);
  };

  return (
    <article
      className="ticket-card"
      data-priority={ticket.priority.toLowerCase()}
      aria-labelledby={`ticket-${ticket.client_id}-heading`}
    >
      <header className="ticket-header">
        <span className="ticket-number" aria-hidden="true">
          {String(index + 1).padStart(2, "0")}
        </span>
        <div>
          <p className="ticket-id">{ticket.client_id}</p>
          <h3 id={`ticket-${ticket.client_id}-heading`}>{ticket.title}</h3>
        </div>
        <span className="priority-flag">{ticket.priority.toLowerCase()}</span>
      </header>

      <div className="ticket-body">
        <div className="field-group">
          <label htmlFor={`ticket-${ticket.client_id}-title`}>Title for ticket {index + 1}</label>
          <input
            id={`ticket-${ticket.client_id}-title`}
            value={ticket.title}
            minLength={5}
            maxLength={120}
            onChange={(event) => updateField("title", event.target.value)}
            required
          />
        </div>

        <div className="field-group">
          <label htmlFor={`ticket-${ticket.client_id}-description`}>
            Description for ticket {index + 1}
          </label>
          <textarea
            id={`ticket-${ticket.client_id}-description`}
            value={ticket.description}
            minLength={20}
            maxLength={2000}
            rows={4}
            onChange={(event) => updateField("description", event.target.value)}
            required
          />
        </div>

        <div className="ticket-meta-grid">
          <div className="field-group">
            <label htmlFor={`ticket-${ticket.client_id}-priority`}>Priority</label>
            <select
              id={`ticket-${ticket.client_id}-priority`}
              value={ticket.priority}
              onChange={(event) => updateField("priority", event.target.value)}
            >
              {priorities.map((priority) => (
                <option key={priority} value={priority}>
                  {priority.toLowerCase()}
                </option>
              ))}
            </select>
          </div>

          <div className="field-group">
            <label htmlFor={`ticket-${ticket.client_id}-estimate`}>Estimated hours</label>
            <input
              id={`ticket-${ticket.client_id}-estimate`}
              type="number"
              min="0.1"
              max="80"
              step="0.1"
              value={ticket.estimated_hours}
              onChange={(event) => updateField("estimated_hours", Number(event.target.value))}
              required
            />
          </div>

          <div className="field-group">
            <label htmlFor={`ticket-${ticket.client_id}-due`}>Due in days</label>
            <input
              id={`ticket-${ticket.client_id}-due`}
              type="number"
              min="0"
              max="365"
              value={ticket.due_in_days ?? ""}
              onChange={(event) =>
                updateField("due_in_days", event.target.value === "" ? null : Number(event.target.value))
              }
            />
          </div>

          <div className="field-group">
            <label htmlFor={`ticket-${ticket.client_id}-category`}>Category</label>
            <input
              id={`ticket-${ticket.client_id}-category`}
              value={ticket.category ?? ""}
              minLength={2}
              maxLength={32}
              onChange={(event) => updateField("category", event.target.value || null)}
            />
          </div>
        </div>

        <fieldset className="criteria-editor">
          <legend>Acceptance criteria</legend>
          {ticket.acceptance_criteria.map((criterion, criterionIndex) => (
            <div className="criterion-row" key={`${ticket.client_id}-criterion-${criterionIndex}`}>
              <span className="criterion-check" aria-hidden="true">✓</span>
              <label htmlFor={`${ticket.client_id}-criterion-${criterionIndex}`}>
                Criterion {criterionIndex + 1}
              </label>
              <textarea
                id={`${ticket.client_id}-criterion-${criterionIndex}`}
                value={criterion}
                minLength={10}
                maxLength={500}
                rows={2}
                onChange={(event) => updateCriterion(criterionIndex, event.target.value)}
                required
              />
            </div>
          ))}
        </fieldset>

        <div className="dependency-row">
          <span className="dependency-label">Depends on</span>
          {ticket.depends_on.length > 0 ? (
            <ul className="dependency-list" aria-label={`Dependencies for ticket ${index + 1}`}>
              {ticket.depends_on.map((dependency) => (
                <li key={dependency}>{dependency}</li>
              ))}
            </ul>
          ) : (
            <p className="dependency-root">Starts the sequence</p>
          )}
        </div>
      </div>
    </article>
  );
}
