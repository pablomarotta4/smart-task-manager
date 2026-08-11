import { useEffect, useState } from "react";

export default function ProjectDesk({
  project,
  taskCount,
  members,
  memberMutationPhase,
  memberError,
  mutationPhase,
  error,
  onPlanFollowUp,
  onUpdateProject,
  onDeleteProject,
  onAddMember,
  onRemoveMember,
}) {
  const [openPanel, setOpenPanel] = useState(null);
  const [draft, setDraft] = useState({
    name: project.name,
    objective: project.objective ?? "",
  });
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [participantUsername, setParticipantUsername] = useState("");
  const [memberPendingRemoval, setMemberPendingRemoval] = useState(null);

  useEffect(() => {
    setDraft({ name: project.name, objective: project.objective ?? "" });
    setConfirmingDelete(false);
    setParticipantUsername("");
    setMemberPendingRemoval(null);
  }, [project.id, project.name, project.objective]);

  const togglePanel = (panel) => {
    setOpenPanel((current) => current === panel ? null : panel);
    setConfirmingDelete(false);
    setMemberPendingRemoval(null);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    onUpdateProject(project, {
      name: draft.name.trim(),
      objective: draft.objective.trim() || null,
    });
  };

  const handleAddMember = async (event) => {
    event.preventDefault();
    const added = await onAddMember(participantUsername.trim());
    if (added) setParticipantUsername("");
  };

  const handleRemoveMember = async () => {
    const removed = await onRemoveMember(memberPendingRemoval);
    if (removed) setMemberPendingRemoval(null);
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
            aria-expanded={openPanel === "people"}
            onClick={() => togglePanel("people")}
          >
            People
          </button>
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
                maxLength={150}
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

      {openPanel === "people" ? (
        <div className="project-desk-panel project-people" aria-label="Project participants">
          <div className="project-people-heading">
            <div>
              <p className="section-index">Access and ownership</p>
              <h3>Project participants</h3>
            </div>
            <span>{members.length} people</span>
          </div>

          <div className="project-member-list">
            {members.map((member) => (
              <div className="project-member" key={member.userId}>
                <div>
                  <strong>{member.fullName || member.username}</strong>
                  <span>@{member.username}</span>
                </div>
                <div className="project-member-actions">
                  {member.owner ? (
                    <span className="project-owner-badge">Owner</span>
                  ) : (
                    <button
                      className="text-action"
                      type="button"
                      disabled={memberMutationPhase !== "idle"}
                      aria-label={`Remove ${member.fullName || member.username}`}
                      onClick={() => setMemberPendingRemoval(member)}
                    >
                      Remove
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>

          <form className="project-member-add" onSubmit={handleAddMember}>
            <div className="field-group">
              <label htmlFor={`participant-username-${project.id}`}>Participant username</label>
              <input
                id={`participant-username-${project.id}`}
                value={participantUsername}
                onChange={(event) => setParticipantUsername(event.target.value)}
                maxLength={50}
                autoComplete="off"
                required
              />
            </div>
            <button
              className="primary-action compact-action"
              type="submit"
              disabled={memberMutationPhase !== "idle" || participantUsername.trim().length === 0}
            >
              {memberMutationPhase === "adding" ? "Adding participant…" : "Add participant"}
            </button>
          </form>

          {memberError ? <p className="project-mutation-error" role="alert">{memberError}</p> : null}

          {memberPendingRemoval ? (
            <div
              className="danger-confirmation"
              role="alertdialog"
              aria-labelledby={`remove-member-${memberPendingRemoval.userId}`}
            >
              <div>
                <h3 id={`remove-member-${memberPendingRemoval.userId}`}>
                  Remove {memberPendingRemoval.fullName || memberPendingRemoval.username}?
                </h3>
                <p>
                  This removes their project access and unassigns their tickets from this project.
                </p>
              </div>
              <div>
                <button
                  className="text-action"
                  type="button"
                  disabled={memberMutationPhase === "removing"}
                  onClick={() => setMemberPendingRemoval(null)}
                >
                  Cancel
                </button>
                <button
                  className="danger-action is-confirm"
                  type="button"
                  disabled={memberMutationPhase === "removing"}
                  onClick={handleRemoveMember}
                >
                  {memberMutationPhase === "removing" ? "Removing participant…" : "Remove participant"}
                </button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </aside>
  );
}
