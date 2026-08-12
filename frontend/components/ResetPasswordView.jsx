import { useState } from "react";

const ACCOUNT_ACTION_MESSAGES = {
  ACCOUNT_ACTION_INVALID: "This reset link is invalid.",
  ACCOUNT_ACTION_EXPIRED: "This reset link has expired.",
  ACCOUNT_ACTION_USED: "This reset link has already been used.",
  ACCOUNT_ACTION_SUPERSEDED: "A newer reset link replaced this one.",
};

export const describeAccountActionFailure = (error, subject = "link") => {
  if (error?.status === 429) {
    return `Too many attempts. Try again${error.retryAfterSeconds
      ? ` in ${error.retryAfterSeconds} seconds`
      : " later"}.`;
  }
  const message = ACCOUNT_ACTION_MESSAGES[error?.code];
  if (message) {
    return subject === "verification"
      ? message
        .replace("reset link", "verification link")
        .replace("newer reset link", "newer verification link")
      : message;
  }
  return `We couldn't use this ${subject === "verification" ? "verification link" : "reset link"}. Try again.`;
};

export default function ResetPasswordView({
  client,
  token,
  onPasswordReset,
  captureActionContext,
  onReturnToLogin,
}) {
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (password !== confirmation) {
      setError("The passwords do not match.");
      return;
    }
    setError("");
    setPhase("submitting");
    const actionContext = captureActionContext?.();
    try {
      await client.confirmPasswordReset({ token, password });
      onPasswordReset?.(actionContext);
      setPassword("");
      setConfirmation("");
      setPhase("success");
    } catch (requestError) {
      setError(describeAccountActionFailure(requestError));
      setPhase("idle");
    }
  };

  return (
    <main className="account-action-shell">
      <div className="paper-noise" aria-hidden="true" />
      <section className="account-action-card" aria-labelledby="reset-password-title">
        <span className="status-chip">Account recovery</span>
        <p className="section-index">Choose a new password</p>
        <h1 id="reset-password-title">Reset your password</h1>

        {!token ? (
          <p className="error-banner" role="alert">This reset link is incomplete.</p>
        ) : phase === "success" ? (
          <p className="account-action-result" role="status">
            Your password has been reset. You can sign in with the new password.
          </p>
        ) : (
          <form className="stacked-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="new-password">New password</label>
              <input
                id="new-password"
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>
            <div className="field-group">
              <label htmlFor="confirm-new-password">Confirm new password</label>
              <input
                id="confirm-new-password"
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                required
              />
            </div>
            {error ? <p className="error-banner" role="alert">{error}</p> : null}
            <button
              className="primary-action"
              type="submit"
              disabled={phase === "submitting"}
              aria-busy={phase === "submitting"}
            >
              <span>{phase === "submitting" ? "Resetting…" : "Reset password"}</span>
              <span aria-hidden="true">↗</span>
            </button>
          </form>
        )}

        <button className="text-action account-action-return" type="button" onClick={onReturnToLogin}>
          Return to sign in
        </button>
      </section>
    </main>
  );
}
