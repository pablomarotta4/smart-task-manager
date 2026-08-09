import QualityPanel from "./QualityPanel";
import TicketEditor from "./TicketEditor";

export default function DraftEditor({
  draft,
  quality,
  model,
  revisionCount,
  confirming,
  onChange,
  onConfirm,
}) {
  const updateProjectField = (field, value) => {
    onChange({ ...draft, [field]: value });
  };

  const updateListItem = (field, index, value) => {
    const items = draft[field].map((item, currentIndex) =>
      currentIndex === index ? value : item,
    );
    updateProjectField(field, items);
  };

  const updateTicket = (index, ticket) => {
    const tickets = draft.tickets.map((currentTicket, currentIndex) =>
      currentIndex === index ? ticket : currentTicket,
    );
    updateProjectField("tickets", tickets);
  };

  return (
    <section className="draft-stage" aria-labelledby="draft-title">
      <div className="draft-heading-grid">
        <div className="draft-heading-copy">
          <p className="section-index">02 / Review</p>
          <h2 id="draft-title">{draft.name}</h2>
          <p className="muted-copy">
            Edit the project and ticket language before anything is written to the task board.
          </p>
        </div>
        <QualityPanel quality={quality} model={model} revisionCount={revisionCount} />
      </div>

      <form className="draft-form" onSubmit={onConfirm}>
        <section className="project-details-card" aria-labelledby="project-details-title">
          <p className="section-index">Project definition</p>
          <h3 id="project-details-title">Project details</h3>
          <div className="field-group">
            <label htmlFor="draft-name">Project name</label>
            <input
              id="draft-name"
              value={draft.name}
              minLength={3}
              maxLength={150}
              onChange={(event) => updateProjectField("name", event.target.value)}
              required
            />
          </div>

          <div className="field-group">
            <label htmlFor="draft-objective">Objective</label>
            <textarea
              id="draft-objective"
              value={draft.objective}
              minLength={20}
              maxLength={2000}
              rows={4}
              onChange={(event) => updateProjectField("objective", event.target.value)}
              required
            />
          </div>

          {draft.assumptions.length > 0 ? (
            <fieldset className="inline-list-editor">
              <legend>Assumptions</legend>
              {draft.assumptions.map((assumption, index) => (
                <div key={`assumption-${index}`}>
                  <label htmlFor={`assumption-${index}`}>Assumption {index + 1}</label>
                  <input
                    id={`assumption-${index}`}
                    value={assumption}
                    minLength={3}
                    maxLength={255}
                    onChange={(event) =>
                      updateListItem("assumptions", index, event.target.value)
                    }
                    required
                  />
                </div>
              ))}
            </fieldset>
          ) : null}

          {draft.risks.length > 0 ? (
            <fieldset className="inline-list-editor">
              <legend>Risks</legend>
              {draft.risks.map((risk, index) => (
                <div key={`risk-${index}`}>
                  <label htmlFor={`risk-${index}`}>Risk {index + 1}</label>
                  <input
                    id={`risk-${index}`}
                    value={risk}
                    minLength={3}
                    maxLength={255}
                    onChange={(event) => updateListItem("risks", index, event.target.value)}
                    required
                  />
                </div>
              ))}
            </fieldset>
          ) : null}
        </section>

        <section className="tickets-section" aria-labelledby="tickets-title">
          <div className="tickets-heading">
            <p className="section-index">First backlog</p>
            <h3 id="tickets-title">{draft.tickets.length} proposed tickets</h3>
            <p>Ordered by dependency. Edit freely; identifiers and links stay stable.</p>
          </div>
          <div className="ticket-sequence">
            {draft.tickets.map((ticket, index) => (
              <TicketEditor
                key={ticket.client_id}
                ticket={ticket}
                index={index}
                onChange={(updatedTicket) => updateTicket(index, updatedTicket)}
              />
            ))}
          </div>
        </section>

        <footer className="confirmation-bar">
          <div>
            <strong>Human confirmation required</strong>
            <p>This creates one project and {draft.tickets.length} TODO tickets.</p>
          </div>
          <button
            className="primary-action"
            type="submit"
            disabled={confirming}
            aria-busy={confirming}
          >
            <span>{confirming ? "Creating project…" : "Confirm and create project"}</span>
            <span aria-hidden="true">{confirming ? "◌" : "↗"}</span>
          </button>
        </footer>
      </form>
    </section>
  );
}
