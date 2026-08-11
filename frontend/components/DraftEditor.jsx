import QualityPanel from "./QualityPanel";
import TicketEditor from "./TicketEditor";

export default function DraftEditor({
  draft,
  quality,
  model,
  revisionCount,
  planningTarget,
  confirming,
  onChange,
  onConfirm,
}) {
  const isExistingTaskPlan = planningTarget != null;
  const openQuestions = draft.openQuestions ?? [];
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
            {isExistingTaskPlan
              ? "Edit the selected ticket and proposed child tickets before anything changes on the board."
              : "Edit the project and ticket language before anything is written to the task board."}
          </p>
        </div>
        <QualityPanel quality={quality} model={model} revisionCount={revisionCount} />
      </div>

      <form className="draft-form" onSubmit={onConfirm}>
        <section className="project-details-card" aria-labelledby="project-details-title">
          <p className="section-index">
            {isExistingTaskPlan ? "Selected ticket" : "Project definition"}
          </p>
          <h3 id="project-details-title">
            {isExistingTaskPlan ? "Ticket refinement" : "Project details"}
          </h3>
          <div className="field-group">
            <label htmlFor="draft-name">
              {isExistingTaskPlan ? "Refined ticket title" : "Project name"}
            </label>
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
            <label htmlFor="draft-objective">
              {isExistingTaskPlan ? "Refined ticket objective" : "Objective"}
            </label>
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

          {openQuestions.length > 0 ? (
            <section className="open-decisions" aria-labelledby="open-decisions-title">
              <div className="open-decisions-copy">
                <p className="decision-kicker">Optional context / {openQuestions.length}</p>
                <h3 id="open-decisions-title">Open decisions</h3>
                <p>
                  You can confirm this draft now. These questions make the AI&apos;s uncertainty
                  visible so you can edit the wording or revisit the decisions later.
                </p>
              </div>
              <div className="open-question-list">
                {openQuestions.map((question, index) => (
                  <div className="open-question-row" key={`open-question-${index}`}>
                    <span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
                    <div className="field-group">
                      <label htmlFor={`open-question-${index}`}>
                        Open question {index + 1}
                      </label>
                      <textarea
                        id={`open-question-${index}`}
                        value={question}
                        minLength={10}
                        maxLength={255}
                        rows={2}
                        onChange={(event) =>
                          updateListItem("openQuestions", index, event.target.value)
                        }
                        required
                      />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ) : null}

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
            <p className="section-index">
              {isExistingTaskPlan ? "Child-ticket plan" : "First backlog"}
            </p>
            <h3 id="tickets-title">
              {draft.tickets.length} proposed {isExistingTaskPlan ? "child tickets" : "tickets"}
            </h3>
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
            <p>
              {isExistingTaskPlan
                ? `This refines ticket #${planningTarget.task.id} and adds ${draft.tickets.length} child tickets to ${planningTarget.project.name}.`
                : `This creates one project and ${draft.tickets.length} TODO tickets.`}
            </p>
          </div>
          <button
            className="primary-action"
            type="submit"
            disabled={confirming}
            aria-busy={confirming}
          >
            <span>
              {confirming
                ? (isExistingTaskPlan ? "Adding tickets…" : "Creating project…")
                : (isExistingTaskPlan ? "Confirm and add tickets" : "Confirm and create project")}
            </span>
            <span aria-hidden="true">{confirming ? "◌" : "↗"}</span>
          </button>
        </footer>
      </form>
    </section>
  );
}
