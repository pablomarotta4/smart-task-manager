export default function AccountSection({ user, onLogout }) {
  const displayName = user.fullName || user.username;

  return (
    <section className="account-stage" aria-labelledby="account-title">
      <header className="account-hero">
        <div>
          <p className="section-index">05 / Identity</p>
          <h1 id="account-title">Account</h1>
          <p>Your local workspace identity and session controls.</p>
        </div>
        <span className="account-status">Active session</span>
      </header>

      <div className="account-grid">
        <article className="account-card account-profile">
          <span className="account-monogram" aria-hidden="true">
            {displayName.slice(0, 2).toUpperCase()}
          </span>
          <div>
            <p>Signed in as</p>
            <h2>{displayName}</h2>
            <strong>{user.username}</strong>
          </div>
        </article>

        <article className="account-card account-details">
          <p className="section-index">Workspace access</p>
          <dl>
            <div>
              <dt>Email</dt>
              <dd>{user.email || "Not provided"}</dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd>{user.role || "Workspace member"}</dd>
            </div>
            <div>
              <dt>Session</dt>
              <dd>Stored in this browser tab</dd>
            </div>
          </dl>
        </article>

        <article className="account-card account-boundary">
          <p className="section-index">Session boundary</p>
          <h2>Leave the workspace safely</h2>
          <p>
            Signing out clears the local session token. Your saved projects and tickets
            remain in the workspace.
          </p>
          <button className="primary-action" type="button" onClick={onLogout}>
            <span>Sign out of workspace</span><span aria-hidden="true">↗</span>
          </button>
        </article>
      </div>
    </section>
  );
}
