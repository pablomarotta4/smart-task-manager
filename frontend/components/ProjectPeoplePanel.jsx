import { useEffect, useState } from "react";

import {
  canChangeProjectMemberRole,
  canInviteProjectRole,
  canRemoveProjectMember,
  canRevokeProjectInvitation,
} from "../lib/projectPermissions";

const formatExpiry = (value) => {
  if (!value) return "Expiry unavailable";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "Expiry unavailable";
  return `Expires ${new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(parsed)}`;
};

const displayName = (member) => member.fullName || member.username;

export default function ProjectPeoplePanel({
  project,
  members,
  invitations,
  actorRole,
  loadPhase,
  loadError,
  mutationPhase,
  mutationError,
  onInvite,
  onRevokeInvitation,
  onUpdateMemberRole,
  onRemoveMember,
  onRetry,
}) {
  const [inviteDraft, setInviteDraft] = useState({ email: "", role: "MEMBER" });
  const [createdInviteUrl, setCreatedInviteUrl] = useState(null);
  const [copyNotice, setCopyNotice] = useState("");
  const [memberPendingRemoval, setMemberPendingRemoval] = useState(null);
  const canInvite = canInviteProjectRole({
    actorRole,
    invitationRole: inviteDraft.role,
  });

  useEffect(() => {
    setInviteDraft({ email: "", role: "MEMBER" });
    setCreatedInviteUrl(null);
    setCopyNotice("");
    setMemberPendingRemoval(null);
  }, [project.id]);

  const handleInvite = async (event) => {
    event.preventDefault();
    setCreatedInviteUrl(null);
    setCopyNotice("");
    const created = await onInvite(project.id, {
      email: inviteDraft.email.trim(),
      role: inviteDraft.role,
    });
    if (!created) return;
    setInviteDraft((current) => ({ ...current, email: "" }));
    setCreatedInviteUrl(created.inviteUrl || null);
  };

  const handleCopy = async () => {
    if (!createdInviteUrl) return;
    try {
      await navigator.clipboard.writeText(createdInviteUrl);
      setCopyNotice("Invite link copied. Share it through a trusted channel.");
    } catch {
      setCopyNotice("The invite link could not be copied. Create a new invitation if needed.");
    }
  };

  const handleRemove = async () => {
    const removed = await onRemoveMember(project.id, memberPendingRemoval);
    if (removed) setMemberPendingRemoval(null);
  };

  return (
    <div className="project-desk-panel project-people" aria-label="Project participants">
      <div className="project-people-heading">
        <div>
          <p className="section-index">Access and ownership</p>
          <h3>Project people</h3>
        </div>
        <span>{members.length} people</span>
      </div>

      {loadPhase === "loading" ? (
        <div className="archive-status" role="status" aria-live="polite">
          <span className="status-orbit" aria-hidden="true" />
          <p>Reading project people…</p>
        </div>
      ) : null}

      {loadError ? (
        <div className="project-people-load-error">
          <p className="project-mutation-error" role="alert">{loadError.message}</p>
          {loadError.retryable ? (
            <button className="text-action" type="button" onClick={onRetry}>Try again</button>
          ) : null}
        </div>
      ) : null}

      {loadPhase !== "loading" && !loadError && members.length === 0 ? (
        <p className="project-people-empty">No participants are listed for this project.</p>
      ) : null}

      {loadPhase !== "loading" && !loadError && members.length > 0 ? (
        <ul className="project-member-list">
          {members.map((member) => {
            const name = displayName(member);
            const canChangeRole = canChangeProjectMemberRole({
              actorRole,
              targetRole: member.role,
              nextRole: member.role === "MEMBER" ? "MANAGER" : "MEMBER",
            });
            return (
              <li className="project-member" key={member.userId}>
                <div>
                  <strong>{name}</strong>
                  <span>@{member.username}</span>
                </div>
                <div className="project-member-actions">
                  {canChangeRole ? (
                    <select
                      aria-label={`Change ${name} role`}
                      value={member.role}
                      disabled={mutationPhase !== "idle"}
                      onChange={(event) => onUpdateMemberRole(
                        project.id,
                        member,
                        event.target.value,
                      )}
                    >
                      <option value="MEMBER">Member</option>
                      <option value="MANAGER">Manager</option>
                    </select>
                  ) : member.role === "OWNER" || member.role === "MANAGER" || member.role === "MEMBER" ? (
                    <span className="project-owner-badge">{member.role.toLowerCase()}</span>
                  ) : null}
                  {canRemoveProjectMember({ actorRole, targetRole: member.role }) ? (
                    <button
                      className="text-action"
                      type="button"
                      disabled={mutationPhase !== "idle"}
                      aria-label={`Remove ${name}`}
                      onClick={() => setMemberPendingRemoval(member)}
                    >
                      Remove
                    </button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      ) : null}

      {canInvite && !loadError ? (
        <form className="project-member-add project-invitation-form" onSubmit={handleInvite}>
          <div className="field-group">
            <label htmlFor={`invitation-email-${project.id}`}>Invite email</label>
            <input
              id={`invitation-email-${project.id}`}
              type="email"
              autoComplete="email"
              maxLength={255}
              value={inviteDraft.email}
              onChange={(event) => setInviteDraft((current) => ({
                ...current,
                email: event.target.value,
              }))}
              required
            />
          </div>
          <div className="field-group">
            <label htmlFor={`invitation-role-${project.id}`}>Invitation role</label>
            <select
              id={`invitation-role-${project.id}`}
              value={inviteDraft.role}
              disabled={actorRole === "MANAGER" || mutationPhase !== "idle"}
              onChange={(event) => setInviteDraft((current) => ({
                ...current,
                role: event.target.value,
              }))}
            >
              <option value="MEMBER">Member</option>
              {actorRole === "OWNER" ? <option value="MANAGER">Manager</option> : null}
            </select>
          </div>
          <button
            className="primary-action compact-action"
            type="submit"
            disabled={mutationPhase !== "idle" || inviteDraft.email.trim().length === 0}
          >
            {mutationPhase === "inviting" ? "Sending invitation…" : "Send invitation"}
          </button>
        </form>
      ) : null}

      {createdInviteUrl ? (
        <div className="project-invite-copy">
          <p>The invitation is ready. This is the only time its private link is available.</p>
          <button className="text-action" type="button" onClick={handleCopy}>Copy invite link</button>
        </div>
      ) : null}
      {copyNotice ? <p className="project-copy-notice" role="status">{copyNotice}</p> : null}

      {loadPhase !== "loading" && !loadError ? (
        <section className="project-invitations" aria-labelledby={`pending-invites-${project.id}`}>
          <div className="project-invitations-heading">
            <h4 id={`pending-invites-${project.id}`}>Pending invitations</h4>
            <span>{invitations.length}</span>
          </div>
          {invitations.length === 0 ? (
            <p className="project-people-empty">No invitations are waiting for a response.</p>
          ) : (
            <ul className="project-invitation-list">
              {invitations.map((invitation) => (
                <li key={invitation.invitationId}>
                  <div>
                    <strong>{invitation.email}</strong>
                    <span>{formatExpiry(invitation.expiresAt)}</span>
                  </div>
                  <div className="project-member-actions">
                    <span className="project-pending-badge">Pending</span>
                    <span className="project-owner-badge">{invitation.role.toLowerCase()}</span>
                    {canRevokeProjectInvitation({
                      actorRole,
                      invitationRole: invitation.role,
                    }) ? (
                      <button
                        className="text-action"
                        type="button"
                        disabled={mutationPhase !== "idle"}
                        aria-label={`Revoke ${invitation.email} invitation`}
                        onClick={() => onRevokeInvitation(project.id, invitation)}
                      >
                        Revoke
                      </button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      {mutationError ? (
        <p className="project-mutation-error" role="alert">{mutationError.message}</p>
      ) : null}

      {memberPendingRemoval ? (
        <div
          className="danger-confirmation"
          role="alertdialog"
          aria-labelledby={`remove-member-${memberPendingRemoval.userId}`}
        >
          <div>
            <h3 id={`remove-member-${memberPendingRemoval.userId}`}>
              Remove {displayName(memberPendingRemoval)}?
            </h3>
            <p>This removes their project access and unassigns their tickets from this project.</p>
          </div>
          <div>
            <button
              className="text-action"
              type="button"
              disabled={mutationPhase === "removing"}
              onClick={() => setMemberPendingRemoval(null)}
            >
              Cancel
            </button>
            <button
              className="danger-action is-confirm"
              type="button"
              disabled={mutationPhase === "removing"}
              onClick={handleRemove}
            >
              {mutationPhase === "removing" ? "Removing participant…" : "Remove participant"}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
