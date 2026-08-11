import { useState } from "react";

const EMPTY_PROJECT = { name: "", objective: "" };

export default function ProjectCreatePanel({ creating, error, onCreate, onCancel }) {
  const [draft, setDraft] = useState(EMPTY_PROJECT);

  const handleSubmit = async (event) => {
    event.preventDefault();
    const created = await onCreate({
      name: draft.name.trim(),
      objective: draft.objective.trim() || null,
    });
    if (created) {
      setDraft(EMPTY_PROJECT);
      onCancel();
    }
  };

  return (
    <section className="project-create-panel" aria-labelledby="new-project-title">
      <header>
        <div>
          <p className="section-index">New folio</p>
          <h2 id="new-project-title">Start with the work you know</h2>
        </div>
        <button className="text-action" type="button" onClick={onCancel}>
          Close
        </button>
      </header>

      <form className="project-create-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="new-project-name">Project name</label>
          <input
            id="new-project-name"
            value={draft.name}
            onChange={(event) => setDraft((current) => ({
              ...current,
              name: event.target.value,
            }))}
            maxLength={150}
            required
          />
        </div>
        <div className="field-group">
          <label htmlFor="new-project-objective">Objective</label>
          <textarea
            id="new-project-objective"
            value={draft.objective}
            onChange={(event) => setDraft((current) => ({
              ...current,
              objective: event.target.value,
            }))}
            rows={3}
            maxLength={2000}
          />
        </div>
        {error ? <p className="project-mutation-error" role="alert">{error}</p> : null}
        <button
          className="primary-action"
          type="submit"
          disabled={creating || draft.name.trim().length === 0}
          aria-busy={creating}
        >
          <span>{creating ? "Creating project…" : "Create project"}</span>
          <span aria-hidden="true">↗</span>
        </button>
      </form>
    </section>
  );
}
