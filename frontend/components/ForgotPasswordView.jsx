import { useState } from "react";

const RESET_REQUEST_ACCEPTED =
  "If an account matches that email, a reset link will arrive shortly.";

const retryMessage = (error) => error?.status === 429
  ? `Too many requests. Try again${error.retryAfterSeconds
    ? ` in ${error.retryAfterSeconds} seconds`
    : " later"}.`
  : "We couldn't submit that request. Try again.";

export default function ForgotPasswordView({ client, onReturnToLogin }) {
  const [email, setEmail] = useState("");
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("submitting");
    try {
      await client.requestPasswordReset({ email: email.trim() });
      setPhase("accepted");
    } catch (requestError) {
      setError(retryMessage(requestError));
      setPhase("idle");
    }
  };

  return (
    <main className="account-action-shell">
      <div className="paper-noise" aria-hidden="true" />
      <section className="account-action-card" aria-labelledby="forgot-password-title">
        <span className="status-chip">Account recovery</span>
        <p className="section-index">Secure access</p>
        <h1 id="forgot-password-title">Reset your password</h1>
        <p className="muted-copy">
          Enter the email attached to your workspace. We will send instructions when a
          matching account can be recovered.
        </p>

        {phase === "accepted" ? (
          <p className="account-action-result" role="status">{RESET_REQUEST_ACCEPTED}</p>
        ) : (
          <form className="stacked-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="recovery-email">Account email</label>
              <input
                id="recovery-email"
                type="email"
                autoComplete="email"
                maxLength={255}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
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
              <span>{phase === "submitting" ? "Sending…" : "Send reset link"}</span>
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
