import { useEffect, useState } from "react";

export default function ProjectDesk({
  project,
  taskCount,
  mutationPhase,
  error,
  onPlanFollowUp,
  onUpdateProject,
  onDeleteProject,
}) {
  const [openPanel, setOpenPanel] = useState(null);
  const [draft, setDraft] = useState({
    name: project.name,
    objective: project.objective ?? "",
  });
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  useEffect(() => {
    setDraft({ name: project.name, objective: project.objective ?? "" });
    setConfirmingDelete(false);
  }, [project.id, project.name, project.objective]);

  const togglePanel = (panel) => {
    setOpenPanel((current) => current === panel ? null : panel);
    setConfirmingDelete(false);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    onUpdateProject(project, {
      name: draft.name.trim(),
      objective: draft.objective.trim() || null,
    });
  };

  return (
    <aside className="project-desk" aria-labelledby="project-desk-title">
      <div className="project-desk-heading">
        <div>
          <p className="section-index">Project desk</p>
          <h2 id="project-desk-title">Context before action</h2>
        </div>
        <div className="project-desk-actions">
          <button
            className="text-action"
            type="button"
            aria-expanded={openPanel === "planning"}
            onClick={() => togglePanel("planning")}
          >
            Plan next phase
          </button>
          <button
            className="text-action"
            type="button"
            aria-expanded={openPanel === "settings"}
            onClick={() => togglePanel("settings")}
          >
            Project settings
          </button>
        </div>
      </div>

      {openPanel === "planning" ? (
        <div className="project-desk-panel project-desk-planning">
          <span className="desk-panel-marker" aria-hidden="true">AI</span>
          <div>
            <h3>Start a separate follow-up brief</h3>
            <p>
              The Workshop can use this project as context for a new plan. It does not
              modify this project or its existing tickets.
            </p>
          </div>
          <button
            className="primary-action compact-action"
            type="button"
            onClick={() => onPlanFollowUp(project)}
          >
            <span>Open follow-up brief</span><span aria-hidden="true">↗</span>
          </button>
        </div>
      ) : null}

      {openPanel === "settings" ? (
        <div className="project-desk-panel project-settings" aria-label="Project settings form">
          <form className="project-settings-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor={`project-name-${project.id}`}>Project name</label>
              <input
                id={`project-name-${project.id}`}
                value={draft.name}
                onChange={(event) => setDraft((current) => ({
                  ...current,
                  name: event.target.value,
                }))}
                maxLength={255}
                required
              />
            </div>
            <div className="field-group">
              <label htmlFor={`project-objective-${project.id}`}>Objective</label>
              <textarea
                id={`project-objective-${project.id}`}
                value={draft.objective}
                onChange={(event) => setDraft((current) => ({
                  ...current,
                  objective: event.target.value,
                }))}
                rows={3}
                maxLength={2000}
              />
            </div>
            <div className="project-settings-summary">
              <span>Owner</span>
              <strong>{project.ownerUsername || "Current account"}</strong>
              <span>Tickets</span>
              <strong>{taskCount}</strong>
            </div>
            {error ? <p className="project-mutation-error" role="alert">{error}</p> : null}
            <div className="project-settings-actions">
              <button
                className="primary-action compact-action"
                type="submit"
                disabled={mutationPhase !== "idle" || draft.name.trim().length === 0}
              >
                {mutationPhase === "updating" ? "Saving project…" : "Save project"}
              </button>
              <button
                className="danger-action"
                type="button"
                disabled={mutationPhase !== "idle"}
                onClick={() => setConfirmingDelete(true)}
              >
                Delete project
              </button>
            </div>
          </form>

          {confirmingDelete ? (
            <div className="danger-confirmation" role="alertdialog" aria-labelledby="delete-project-title">
              <div>
                <h3 id="delete-project-title">Delete {project.name}?</h3>
                <p>This deletes every ticket in this project. This action cannot be undone.</p>
              </div>
              <div>
                <button
                  className="text-action"
                  type="button"
                  disabled={mutationPhase === "deleting"}
                  onClick={() => setConfirmingDelete(false)}
                >
                  Cancel
                </button>
                <button
                  className="danger-action is-confirm"
                  type="button"
                  disabled={mutationPhase === "deleting"}
                  onClick={() => onDeleteProject(project)}
                >
                  {mutationPhase === "deleting" ? "Deleting project…" : "Yes, delete project"}
                </button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </aside>
  );
}
