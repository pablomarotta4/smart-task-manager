import { useState } from "react";

import { ApiError, apiClient } from "./api";

const SESSION_KEY = "smart-task-session";

const readSession = () => {
  try {
    const value = sessionStorage.getItem(SESSION_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
};

const errorMessage = (error) =>
  error instanceof Error ? error.message : "Something unexpected happened";

export default function App({ client = apiClient }) {
  const [session, setSession] = useState(readSession);
  const [credentials, setCredentials] = useState({ username: "", password: "" });
  const [prompt, setPrompt] = useState("");
  const [draftResponse, setDraftResponse] = useState(null);
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");

  const handleCredentialChange = (event) => {
    const { name, value } = event.target;
    setCredentials((current) => ({ ...current, [name]: value }));
  };

  const handleLogin = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("logging-in");
    try {
      const authenticated = await client.login(credentials);
      const nextSession = { token: authenticated.token, user: authenticated.user };
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
      setSession(nextSession);
      setPhase("idle");
    } catch (requestError) {
      setError(errorMessage(requestError));
      setPhase("idle");
    }
  };

  const handleGenerate = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("generating");
    try {
      const response = await client.generateProject({
        token: session.token,
        prompt: prompt.trim(),
      });
      setDraftResponse(response);
      setPhase("reviewing");
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      }
      setError(errorMessage(requestError));
      setPhase("idle");
    }
  };

  const handleLogout = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
    setDraftResponse(null);
    setError("");
    setPhase("idle");
  };

  if (!session) {
    return (
      <main>
        <section aria-labelledby="login-title">
          <p>Smart Task Manager</p>
          <h1 id="login-title">Enter the project workshop</h1>
          <p>Sign in to turn a plain-language brief into an editable first backlog.</p>

          <form onSubmit={handleLogin}>
            <label htmlFor="username">Username</label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              value={credentials.username}
              onChange={handleCredentialChange}
              required
            />

            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={credentials.password}
              onChange={handleCredentialChange}
              required
            />

            {error ? <p role="alert">{error}</p> : null}
            <button type="submit" disabled={phase === "logging-in"}>
              {phase === "logging-in" ? "Signing in…" : "Enter workshop"}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main>
      <header>
        <div>
          <p>Project workshop</p>
          <p>{session.user.fullName ?? session.user.username}</p>
        </div>
        <button type="button" onClick={handleLogout}>
          Log out
        </button>
      </header>

      <section aria-labelledby="prompt-title">
        <p>01 / Brief</p>
        <h1 id="prompt-title">What are we building?</h1>
        <p>Describe the outcome in everyday language. Include the important capabilities.</p>

        <form onSubmit={handleGenerate}>
          <label htmlFor="project-prompt">Describe your project</label>
          <textarea
            id="project-prompt"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            minLength={10}
            maxLength={4000}
            rows={6}
            required
          />
          <button
            type="submit"
            disabled={phase === "generating" || prompt.trim().length < 10}
          >
            {phase === "generating" ? "Generating…" : "Generate first plan"}
          </button>
        </form>

        {phase === "generating" ? (
          <p role="status">Building your first backlog. Local generation can take up to 90 seconds.</p>
        ) : null}
        {error ? <p role="alert">{error}</p> : null}
      </section>

      {draftResponse ? (
        <section aria-labelledby="draft-title">
          <p>02 / Draft</p>
          <h2 id="draft-title">{draftResponse.draft.name}</h2>
          <p>{draftResponse.draft.objective}</p>
          <p>{draftResponse.draft.tickets.length} proposed tickets</p>
        </section>
      ) : null}
    </main>
  );
}
