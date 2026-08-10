import { useState } from "react";

export default function ProjectDesk({ project, taskCount, onPlanFollowUp }) {
  const [openPanel, setOpenPanel] = useState(null);

  const togglePanel = (panel) => {
    setOpenPanel((current) => current === panel ? null : panel);
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
        <div className="project-desk-panel project-facts" aria-label="Read-only project settings">
          <div>
            <span>Name</span>
            <strong>{project.name}</strong>
          </div>
          <div>
            <span>Owner</span>
            <strong>{project.ownerUsername || "Current account"}</strong>
          </div>
          <div>
            <span>Tickets</span>
            <strong>{taskCount}</strong>
          </div>
          <p>
            Project details are read-only here because the current API does not support
            updating or deleting projects yet.
          </p>
        </div>
      ) : null}
    </aside>
  );
}
