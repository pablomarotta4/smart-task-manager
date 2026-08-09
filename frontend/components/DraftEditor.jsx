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
    <section aria-labelledby="draft-title">
      <div>
        <div>
          <p>02 / Review</p>
          <h2 id="draft-title">{draft.name}</h2>
          <p>Edit the project and ticket language before anything is written to the task board.</p>
        </div>
        <QualityPanel quality={quality} model={model} revisionCount={revisionCount} />
      </div>

      <form onSubmit={onConfirm}>
        <section aria-labelledby="project-details-title">
          <h3 id="project-details-title">Project details</h3>
          <label htmlFor="draft-name">Project name</label>
          <input
            id="draft-name"
            value={draft.name}
            minLength={3}
            maxLength={150}
            onChange={(event) => updateProjectField("name", event.target.value)}
            required
          />

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

          {draft.assumptions.length > 0 ? (
            <fieldset>
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
            <fieldset>
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

        <section aria-labelledby="tickets-title">
          <div>
            <p>First backlog</p>
            <h3 id="tickets-title">{draft.tickets.length} proposed tickets</h3>
          </div>
          {draft.tickets.map((ticket, index) => (
            <TicketEditor
              key={ticket.client_id}
              ticket={ticket}
              index={index}
              onChange={(updatedTicket) => updateTicket(index, updatedTicket)}
            />
          ))}
        </section>

        <footer>
          <div>
            <strong>Human confirmation required</strong>
            <p>This creates one project and {draft.tickets.length} TODO tickets.</p>
          </div>
          <button type="submit" disabled={confirming}>
            {confirming ? "Creating project…" : "Confirm and create project"}
          </button>
        </footer>
      </form>
    </section>
  );
}
