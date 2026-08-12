import { useState } from "react";

import { describeAccountActionFailure } from "./ResetPasswordView";

export default function VerifyEmailView({ client, token, onVerified, captureActionContext, onReturn }) {
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");

  const handleVerify = async () => {
    setError("");
    setPhase("submitting");
    const actionContext = captureActionContext?.();
    try {
      await client.confirmEmailVerification({ token });
      await onVerified?.(actionContext);
      setPhase("success");
    } catch (requestError) {
      setError(describeAccountActionFailure(requestError, "verification"));
      setPhase("idle");
    }
  };

  return (
    <main className="account-action-shell">
      <div className="paper-noise" aria-hidden="true" />
      <section className="account-action-card" aria-labelledby="verify-email-title">
        <span className="status-chip">Email verification</span>
        <p className="section-index">Confirm your identity</p>
        <h1 id="verify-email-title">Verify your email</h1>
        <p className="muted-copy">
          Verification protects collaboration features and confirms where account messages
          should be delivered.
        </p>

        {!token ? (
          <p className="error-banner" role="alert">This verification link is incomplete.</p>
        ) : phase === "success" ? (
          <p className="account-action-result" role="status">Your email is verified.</p>
        ) : (
          <>
            {error ? <p className="error-banner" role="alert">{error}</p> : null}
            <button
              className="primary-action"
              type="button"
              onClick={handleVerify}
              disabled={phase === "submitting"}
              aria-busy={phase === "submitting"}
            >
              <span>{phase === "submitting" ? "Verifying…" : "Verify email"}</span>
              <span aria-hidden="true">↗</span>
            </button>
          </>
        )}

        <button className="text-action account-action-return" type="button" onClick={onReturn}>
          Return to workspace
        </button>
      </section>
    </main>
  );
}
