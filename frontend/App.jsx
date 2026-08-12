import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError, apiClient } from "./api";
import AccountSection from "./components/AccountSection";
import BoardSection from "./components/BoardSection";
import DraftEditor from "./components/DraftEditor";
import MyWorkSection from "./components/MyWorkSection";
import ProjectsSection from "./components/ProjectsSection";
import RecentPlanningRuns from "./components/RecentPlanningRuns";
import useProjectWorkspace from "./hooks/useProjectWorkspace";

const SESSION_KEY = "smart-task-session";

class SessionChangedError extends Error {
  constructor() {
    super("The authenticated session changed while this request was running");
    this.name = "SessionChangedError";
  }
}

const readSession = () => {
  try {
    const value = sessionStorage.getItem(SESSION_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
};

const errorMessage = (error) => {
  if (error instanceof SessionChangedError) return "";
  return error instanceof Error ? error.message : "Something unexpected happened";
};

export default function App({ client = apiClient }) {
  const [session, setSession] = useState(readSession);
  const [sessionStatus, setSessionStatus] = useState(() => (
    sessionStorage.getItem(SESSION_KEY) ? "checking" : "ready"
  ));
  const [sessionNotice, setSessionNotice] = useState("");
  const [authMode, setAuthMode] = useState("login");
  const [credentials, setCredentials] = useState({ username: "", password: "" });
  const [registration, setRegistration] = useState({
    fullName: "",
    email: "",
    username: "",
    password: "",
  });
  const [prompt, setPrompt] = useState("");
  const [draftResponse, setDraftResponse] = useState(null);
  const [editableDraft, setEditableDraft] = useState(null);
  const [confirmation, setConfirmation] = useState(null);
  const [planningTarget, setPlanningTarget] = useState(null);
  const [activeView, setActiveView] = useState("workshop");
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");
  const [recentRuns, setRecentRuns] = useState([]);
  const [recentRunsPhase, setRecentRunsPhase] = useState("idle");
  const [recentRunsError, setRecentRunsError] = useState("");
  const [busyRunId, setBusyRunId] = useState(null);
  const recentRunsRequestId = useRef(0);
  const sessionRequestId = useRef(0);
  const refreshPromise = useRef(null);
  const sessionToken = session?.token;

  const persistSession = useCallback((authenticated) => {
    const nextSession = { token: authenticated.token, user: authenticated.user };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    setSessionStatus("ready");
    setSessionNotice("");
    return nextSession;
  }, []);

  const clearWorkspaceSession = useCallback((notice = "") => {
    sessionRequestId.current += 1;
    recentRunsRequestId.current += 1;
    refreshPromise.current = null;
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
    setSessionStatus("ready");
    setSessionNotice(notice);
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    setPlanningTarget(null);
    setPrompt("");
    setActiveView("workshop");
    setRecentRuns([]);
    setRecentRunsError("");
    setRecentRunsPhase("idle");
    setBusyRunId(null);
    setAuthMode("login");
    setCredentials((current) => ({ ...current, password: "" }));
    setRegistration({ fullName: "", email: "", username: "", password: "" });
    setError("");
    setPhase("idle");
  }, []);

  const refreshAccessToken = useCallback(() => {
    if (refreshPromise.current) return refreshPromise.current;
    const requestId = sessionRequestId.current;
    const pendingRefresh = client.refreshSession()
      .then((authenticated) => {
        if (sessionRequestId.current !== requestId) {
          throw new SessionChangedError();
        }
        persistSession(authenticated);
        return authenticated.token;
      })
      .finally(() => {
        if (refreshPromise.current === pendingRefresh) {
          refreshPromise.current = null;
        }
      });
    refreshPromise.current = pendingRefresh;
    return pendingRefresh;
  }, [client, persistSession]);

  const runAuthenticated = useCallback(async (operation) => {
    if (!sessionToken) {
      throw new ApiError("Your session expired. Sign in again.", { status: 401 });
    }
    const requestId = sessionRequestId.current;
    const returnForCurrentSession = (result) => {
      if (sessionRequestId.current !== requestId) {
        throw new SessionChangedError();
      }
      return result;
    };
    try {
      return returnForCurrentSession(await operation(sessionToken));
    } catch (requestError) {
      if (sessionRequestId.current !== requestId || requestError instanceof SessionChangedError) {
        throw new SessionChangedError();
      }
      if (!(requestError instanceof ApiError) || requestError.status !== 401) {
        throw requestError;
      }
    }

    try {
      const renewedToken = await refreshAccessToken();
      return returnForCurrentSession(await operation(renewedToken));
    } catch (requestError) {
      if (sessionRequestId.current !== requestId || requestError instanceof SessionChangedError) {
        throw new SessionChangedError();
      }
      if (requestError instanceof ApiError && requestError.status === 401) {
        clearWorkspaceSession("Your session expired. Sign in again.");
        throw new ApiError("Your session expired. Sign in again.", { status: 401 });
      }
      throw requestError;
    }
  }, [clearWorkspaceSession, refreshAccessToken, sessionToken]);

  const workspace = useProjectWorkspace({
    client,
    executeAuthenticated: runAuthenticated,
    sessionKey: session?.user?.id ?? null,
    currentUserId: session?.user?.id ?? null,
  });
  const {
    projects,
    selectedProject,
    projectTasks,
    projectMembers,
    workItems,
    selectedTask,
    projectPhase,
    projectError,
    memberLoadPhase,
    memberLoadError,
    projectMutationPhase,
    projectMutationError,
    taskMutationPhase,
    taskError,
    memberMutationPhase,
    memberError,
    permissions: selectedProjectPermissions,
  } = workspace;

  useEffect(() => {
    if (!sessionToken || sessionStatus !== "checking") return undefined;
    const requestId = sessionRequestId.current + 1;
    sessionRequestId.current = requestId;
    let active = true;

    const validateSession = async () => {
      try {
        const user = await client.getCurrentUser({ token: sessionToken });
        if (!active || sessionRequestId.current !== requestId) return;
        persistSession({ token: sessionToken, user });
      } catch (requestError) {
        if (!active || sessionRequestId.current !== requestId) return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          try {
            const authenticated = await client.refreshSession();
            if (!active || sessionRequestId.current !== requestId) return;
            persistSession(authenticated);
          } catch (refreshError) {
            if (!active || sessionRequestId.current !== requestId) return;
            if (refreshError instanceof ApiError && refreshError.status === 401) {
              clearWorkspaceSession("Your session expired. Sign in again.");
            } else {
              setSessionNotice("Cannot restore your session while the API is unavailable.");
              setSessionStatus("unavailable");
            }
          }
        } else {
          setSessionNotice("Cannot restore your session while the API is unavailable.");
          setSessionStatus("unavailable");
        }
      }
    };

    void validateSession();
    return () => {
      active = false;
    };
  }, [clearWorkspaceSession, client, persistSession, sessionStatus, sessionToken]);

  const loadRecentRuns = useCallback(async () => {
    if (!sessionToken || sessionStatus !== "ready") {
      recentRunsRequestId.current += 1;
      setRecentRuns([]);
      return;
    }
    const requestId = recentRunsRequestId.current + 1;
    recentRunsRequestId.current = requestId;
    setRecentRunsError("");
    setRecentRunsPhase("loading");
    try {
      const runs = await runAuthenticated((token) => client.getGenerationRuns({ token }));
      if (recentRunsRequestId.current !== requestId) return;
      setRecentRuns(runs);
    } catch (requestError) {
      if (recentRunsRequestId.current !== requestId) return;
      if (!(requestError instanceof ApiError && requestError.status === 401)) {
        setRecentRunsError(errorMessage(requestError));
      }
    } finally {
      if (recentRunsRequestId.current === requestId) {
        setRecentRunsPhase("idle");
      }
    }
  }, [client, runAuthenticated, sessionStatus, sessionToken]);

  useEffect(() => {
    void loadRecentRuns();
  }, [loadRecentRuns]);

  const handleCredentialChange = (event) => {
    const { name, value } = event.target;
    setCredentials((current) => ({ ...current, [name]: value }));
  };

  const handleRegistrationChange = (event) => {
    const { name, value } = event.target;
    setRegistration((current) => ({ ...current, [name]: value }));
  };

  const selectAuthMode = (mode) => {
    setAuthMode(mode);
    setError("");
    setSessionNotice("");
  };

  const handleLogin = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("logging-in");
    try {
      const authenticated = await client.login(credentials);
      sessionRequestId.current += 1;
      persistSession(authenticated);
      setCredentials((current) => ({ ...current, password: "" }));
      setPhase("idle");
    } catch (requestError) {
      setError(errorMessage(requestError));
      setPhase("idle");
    }
  };

  const handleRegister = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("registering");
    try {
      const authenticated = await client.register(registration);
      sessionRequestId.current += 1;
      persistSession(authenticated);
      setRegistration({ fullName: "", email: "", username: "", password: "" });
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
      const generationRequest = {
        prompt: prompt.trim(),
      };
      const response = await runAuthenticated((token) => planningTarget
        ? client.generateTaskPlan({
          ...generationRequest,
          token,
          projectId: planningTarget.project.id,
          taskId: planningTarget.task.id,
        })
        : client.generateProject({ ...generationRequest, token }));
      setDraftResponse(response);
      setEditableDraft(structuredClone(response.draft));
      setConfirmation(null);
      setPhase("reviewing");
      void loadRecentRuns();
    } catch (requestError) {
      setError(errorMessage(requestError));
      setPhase("idle");
      void loadRecentRuns();
    }
  };

  const handleConfirm = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("confirming");
    try {
      const response = await runAuthenticated((token) => client.confirmProject({
        token,
        runId: draftResponse.runId,
        draft: editableDraft,
      }));
      setConfirmation(response);
      setPhase("confirmed");
      void loadRecentRuns();
    } catch (requestError) {
      setError(errorMessage(requestError));
      setPhase("reviewing");
    }
  };

  const handleStartOver = () => {
    setActiveView("workshop");
    setPrompt("");
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    setPlanningTarget(null);
    setError("");
    setPhase("idle");
  };

  const handleOpenProjects = async (projectId = null) => {
    setActiveView("projects");
    await workspace.openProjects(projectId);
  };

  const handleSelectProject = (project) => workspace.selectProject(project);
  const handleSelectBoardProject = (project) => workspace.selectProject(
    project,
    { includeMembers: true },
  );

  const handleRetryProjects = () => {
    if (selectedProject) {
      void workspace.selectProject(selectedProject);
      return;
    }
    void handleOpenProjects();
  };

  const handleDeleteProject = async (projectId) => {
    const deleted = await workspace.deleteProject(projectId);
    if (deleted) {
      setActiveView("projects");
    }
    return deleted;
  };

  const handleOpenBoard = async (projectId = selectedProject?.id ?? null) => {
    setActiveView("board");
    await workspace.openBoard(projectId);
  };

  const handleOpenMyWork = async () => {
    setActiveView("my-work");
    await workspace.openMyWork();
  };

  const handlePlanFollowUp = (project) => {
    setPlanningTarget(null);
    setPrompt(
      `Create a follow-up project plan for "${project.name}". `
      + `Use this existing objective as context: ${project.objective || "No objective recorded"}. `
      + "Focus on the next useful phase and produce a separate, editable backlog.",
    );
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    setError("");
    setPhase("idle");
    setActiveView("workshop");
  };

  const handlePlanTask = (task) => {
    setPlanningTarget({ project: selectedProject, task });
    setPrompt(
      `Break "${task.title}" into an actionable implementation plan. `
      + "Use the existing project context and avoid duplicating current work.",
    );
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    workspace.closeTask();
    setError("");
    setPhase("idle");
    setActiveView("workshop");
  };

  const planningTargetFromRun = (run) => run.mode === "EXISTING_TASK" ? {
    project: { id: run.projectId, name: run.projectName },
    task: { id: run.targetTaskId, title: run.targetTaskTitle },
  } : null;

  const restoreDraft = (response, run) => {
    setPlanningTarget(planningTargetFromRun(run));
    setPrompt(run.prompt);
    setDraftResponse(response);
    setEditableDraft(structuredClone(response.draft));
    setConfirmation(null);
    setError("");
    setPhase("reviewing");
    setActiveView("workshop");
  };

  const handleResumeRun = async (run) => {
    setBusyRunId(run.runId);
    setRecentRunsError("");
    try {
      const response = await runAuthenticated((token) => client.getGenerationRun({
        token,
        runId: run.runId,
      }));
      restoreDraft(response, response);
    } catch (requestError) {
      if (!(requestError instanceof ApiError && requestError.status === 401)) {
        setRecentRunsError(errorMessage(requestError));
      }
    } finally {
      setBusyRunId(null);
    }
  };

  const handleRetryRun = async (run) => {
    setBusyRunId(run.runId);
    setRecentRunsError("");
    setError("");
    setPhase("generating");
    setPlanningTarget(planningTargetFromRun(run));
    setPrompt(run.prompt);
    setActiveView("workshop");
    try {
      const response = await runAuthenticated((token) => client.retryGenerationRun({
        token,
        runId: run.runId,
      }));
      restoreDraft(response, run);
      void loadRecentRuns();
    } catch (requestError) {
      if (!(requestError instanceof ApiError && requestError.status === 401)) {
        setError(errorMessage(requestError));
        setPhase("idle");
        void loadRecentRuns();
      }
    } finally {
      setBusyRunId(null);
    }
  };

  const handleLogout = async () => {
    setPhase("logging-out");
    let notice = "";
    const pendingRefresh = refreshPromise.current;
    sessionRequestId.current += 1;
    try {
      if (pendingRefresh) {
        try {
          await pendingRefresh;
        } catch {
          // The logout request remains idempotent and clears whichever cookie is current.
        }
      }
      await client.logout();
    } catch {
      notice = "Signed out locally. The API could not confirm server-side logout.";
    } finally {
      clearWorkspaceSession(notice);
    }
  };

  if (session && sessionStatus !== "ready") {
    return (
      <main className="session-restore-shell">
        <div className="paper-noise" aria-hidden="true" />
        <section className="session-restore-card" aria-labelledby="session-restore-title">
          <span className="status-chip">Secure session</span>
          <p className="section-index">Checking access</p>
          <h1 id="session-restore-title">
            {sessionStatus === "checking" ? "Restoring your workspace…" : "Workspace unavailable"}
          </h1>
          <p className="muted-copy" role={sessionStatus === "unavailable" ? "alert" : "status"}>
            {sessionStatus === "checking"
              ? "Validating your saved session before loading private project data."
              : sessionNotice}
          </p>
          {sessionStatus === "unavailable" ? (
            <div className="session-restore-actions">
              <button
                className="primary-action"
                type="button"
                onClick={() => setSessionStatus("checking")}
              >
                <span>Try again</span><span aria-hidden="true">↗</span>
              </button>
              <button className="text-action" type="button" onClick={handleLogout}>
                Sign out
              </button>
            </div>
          ) : null}
        </section>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="login-shell">
        <div className="paper-noise" aria-hidden="true" />
        <section className="login-intro" aria-label="Smart Task Manager introduction">
          <div className="brand-lockup">
            <span className="brand-mark">STM</span>
            <span>Smart Task Manager</span>
          </div>
          <div className="login-statement">
            <p className="eyebrow">From idea to first move</p>
            <p className="display-statement">
              Make the work <em>clear.</em>
            </p>
            <p className="login-lede">
              Shape a rough idea into a quality-checked project and an actionable first backlog.
            </p>
          </div>
          <div className="process-strip" aria-label="Planning workflow">
            <span>Brief</span>
            <span>Draft</span>
            <span>Review</span>
            <span>Create</span>
          </div>
        </section>

        <section className="login-panel" aria-labelledby="login-title">
          <div className="login-card">
            <span className="status-chip">Local workspace</span>
            <p className="section-index">01 / Access</p>
            <h1 id="login-title">
              {authMode === "login" ? "Enter the project workshop" : "Create your workspace"}
            </h1>
            <p className="muted-copy">
              {authMode === "login"
                ? "Sign in to turn a plain-language brief into an editable first backlog."
                : "Create an account, then start with a blank project or an AI-assisted plan."}
            </p>

            <div className="auth-mode-switch" aria-label="Choose account access mode">
              <button
                type="button"
                aria-pressed={authMode === "login"}
                onClick={() => selectAuthMode("login")}
              >
                Sign in
              </button>
              <button
                type="button"
                aria-pressed={authMode === "register"}
                onClick={() => selectAuthMode("register")}
              >
                Create account
              </button>
            </div>

            <form
              className="stacked-form"
              onSubmit={authMode === "login" ? handleLogin : handleRegister}
            >
              {authMode === "register" ? (
                <>
                  <div className="field-group">
                    <label htmlFor="fullName">Full name</label>
                    <input
                      id="fullName"
                      name="fullName"
                      autoComplete="name"
                      maxLength={100}
                      value={registration.fullName}
                      onChange={handleRegistrationChange}
                      placeholder="Pablo Marotta"
                      required
                    />
                  </div>
                  <div className="field-group">
                    <label htmlFor="email">Email</label>
                    <input
                      id="email"
                      name="email"
                      type="email"
                      autoComplete="email"
                      maxLength={255}
                      value={registration.email}
                      onChange={handleRegistrationChange}
                      placeholder="you@example.com"
                      required
                    />
                  </div>
                </>
              ) : null}
              <div className="field-group">
                <label htmlFor="username">Username</label>
                <input
                  id="username"
                  name="username"
                  autoComplete="username"
                  minLength={authMode === "register" ? 3 : undefined}
                  maxLength={50}
                  value={authMode === "login" ? credentials.username : registration.username}
                  onChange={authMode === "login"
                    ? handleCredentialChange
                    : handleRegistrationChange}
                  placeholder="pablo-local"
                  required
                />
              </div>

              <div className="field-group">
                <label htmlFor="password">Password</label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete={authMode === "login" ? "current-password" : "new-password"}
                  minLength={authMode === "register" ? 8 : undefined}
                  maxLength={72}
                  value={authMode === "login" ? credentials.password : registration.password}
                  onChange={authMode === "login"
                    ? handleCredentialChange
                    : handleRegistrationChange}
                  placeholder={authMode === "login"
                    ? "Your password"
                    : "At least 8 characters"}
                  required
                />
              </div>

              {sessionNotice ? <p className="error-banner" role="alert">{sessionNotice}</p> : null}
              {error ? <p className="error-banner" role="alert">{error}</p> : null}
              <button
                className="primary-action"
                type="submit"
                disabled={phase === "logging-in" || phase === "registering"}
                aria-busy={phase === "logging-in" || phase === "registering"}
              >
                <span>
                  {authMode === "login"
                    ? phase === "logging-in" ? "Signing in…" : "Enter workshop"
                    : phase === "registering" ? "Creating account…" : "Create my workspace"}
                </span>
                <span aria-hidden="true">↗</span>
              </button>
            </form>
            <p className="local-note">
              Access stays in this tab. A protected HttpOnly cookie renews it when needed.
            </p>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="workshop-shell">
      <div className="paper-noise" aria-hidden="true" />
      <header className="topbar">
        <div className="brand-lockup">
          <span className="brand-mark">STM</span>
          <div>
            <p>Project workshop</p>
            <p className="user-name">{session.user.fullName ?? session.user.username}</p>
          </div>
        </div>
        <div className="topbar-actions">
          <nav className="primary-navigation" aria-label="Primary navigation">
            <button
              type="button"
              aria-current={activeView === "workshop" ? "page" : undefined}
              onClick={() => setActiveView("workshop")}
            >
              Workshop
            </button>
            <button
              type="button"
              aria-current={activeView === "projects" ? "page" : undefined}
              onClick={() => handleOpenProjects()}
            >
              Projects
            </button>
            <button
              type="button"
              aria-current={activeView === "board" ? "page" : undefined}
              onClick={() => handleOpenBoard()}
            >
              Board
            </button>
            <button
              type="button"
              aria-current={activeView === "my-work" ? "page" : undefined}
              onClick={handleOpenMyWork}
            >
              My work
            </button>
            <button
              type="button"
              aria-current={activeView === "account" ? "page" : undefined}
              onClick={() => setActiveView("account")}
            >
              Account
            </button>
          </nav>
          <button
            className="text-action"
            type="button"
            onClick={handleLogout}
            disabled={phase === "logging-out"}
          >
            {phase === "logging-out" ? "Signing out…" : "Log out"}{" "}
            <span aria-hidden="true">↗</span>
          </button>
        </div>
      </header>

      {activeView === "projects" ? (
        <ProjectsSection
          projects={projects}
          selectedProject={selectedProject}
          tasks={projectTasks}
          phase={projectPhase}
          error={projectError}
          mutationPhase={projectMutationPhase}
          mutationError={projectMutationError}
          onCreateProject={workspace.createProject}
          onSelectProject={handleSelectProject}
          onRetry={handleRetryProjects}
        />
      ) : activeView === "board" ? (
        <BoardSection
          projects={projects}
          selectedProject={selectedProject}
          tasks={projectTasks}
          phase={projectPhase}
          error={projectError}
          selectedTask={selectedTask}
          savingTask={taskMutationPhase === "updating"}
          taskError={taskError}
          taskMutationPhase={taskMutationPhase}
          projectMembers={projectMembers}
          memberMutationPhase={memberMutationPhase}
          memberError={memberError}
          projectMutationPhase={projectMutationPhase}
          projectMutationError={projectMutationError}
          memberLoadPhase={memberLoadPhase}
          memberLoadError={memberLoadError}
          permissions={selectedProjectPermissions}
          currentUserId={session.user.id}
          onSelectProject={handleSelectBoardProject}
          onSelectTask={workspace.selectTask}
          onCloseTask={workspace.closeTask}
          onSaveTask={workspace.updateTask}
          onCreateTask={workspace.createTask}
          onDeleteTask={workspace.deleteTask}
          onAddProjectMember={workspace.addMember}
          onRemoveProjectMember={workspace.removeMember}
          onPlanTask={handlePlanTask}
          onPlanFollowUp={handlePlanFollowUp}
          onUpdateProject={workspace.updateProject}
          onDeleteProject={handleDeleteProject}
          onRetry={() => handleOpenBoard(selectedProject?.id ?? null)}
        />
      ) : activeView === "my-work" ? (
        <MyWorkSection
          items={workItems}
          phase={projectPhase}
          error={projectError}
          selectedTask={selectedTask}
          savingTask={taskMutationPhase === "updating"}
          taskError={taskError}
          currentUserId={session.user.id}
          onSelectTask={workspace.selectTask}
          onCloseTask={workspace.closeTask}
          onSaveTask={workspace.updateTask}
          onRetry={handleOpenMyWork}
        />
      ) : activeView === "account" ? (
        <AccountSection
          user={session.user}
          onLogout={handleLogout}
          loggingOut={phase === "logging-out"}
        />
      ) : (
        <>
      <section className="prompt-stage" aria-labelledby="prompt-title">
        <div className="prompt-heading">
          <p className="section-index">01 / Brief</p>
          <h1 id="prompt-title">
            {planningTarget ? "Plan this ticket" : "What are we building?"}
          </h1>
          <p className="muted-copy">
            {planningTarget
              ? "Describe the depth, constraints, or delivery shape you want for this ticket."
              : "Describe the outcome in everyday language. Name the capabilities that cannot be missed."}
          </p>
          {planningTarget ? (
            <div className="planning-context-banner" aria-label="Planning target">
              <span>{planningTarget.project.name}</span>
              <strong>{planningTarget.task.title}</strong>
              <p>
                AI receives this ticket and the current project backlog. Nothing changes until
                confirmation.
              </p>
            </div>
          ) : null}
          <div className="prompt-principle">
            <span aria-hidden="true">✦</span>
            <p>Specific verbs make stronger tickets: list, reserve, track, schedule, approve.</p>
          </div>
        </div>

        <div className="prompt-workbench">
          <form onSubmit={handleGenerate}>
            <div className="prompt-label-row">
              <label htmlFor="project-prompt">
                {planningTarget ? "Planning instructions" : "Describe your project"}
              </label>
              <span>{prompt.length} / 4000</span>
            </div>
            <textarea
              id="project-prompt"
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              minLength={3}
              maxLength={4000}
              rows={7}
              placeholder={planningTarget
                ? "Split the selected ticket into a practical, dependency-aware implementation plan…"
                : "Build a neighborhood tool library where residents can list, reserve, borrow, and return shared tools…"}
              required
            />
            <button
              className="primary-action"
              type="submit"
              disabled={phase === "generating" || prompt.trim().length < 3}
              aria-busy={phase === "generating"}
            >
              <span>
                {phase === "generating"
                  ? "Generating…"
                  : (planningTarget ? "Generate task plan" : "Generate first plan")}
              </span>
              <span aria-hidden="true">{phase === "generating" ? "◌" : "↗"}</span>
            </button>
          </form>

          {phase === "generating" ? (
            <div className="generation-status" role="status">
              <span className="status-orbit" aria-hidden="true" />
              <div>
                <strong>
                  {planningTarget ? "Building the child-ticket plan" : "Building your first backlog"}
                </strong>
                <p>
                  Analyzing the brief, drafting tickets, and checking quality. Up to 90 seconds.
                </p>
              </div>
            </div>
          ) : null}
          {error ? <p className="error-banner" role="alert">{error}</p> : null}
        </div>
      </section>

      {draftResponse && editableDraft && !confirmation ? (
        <DraftEditor
          draft={editableDraft}
          quality={draftResponse.quality}
          model={draftResponse.model}
          revisionCount={draftResponse.revisionCount}
          planningTarget={planningTarget}
          confirming={phase === "confirming"}
          onChange={setEditableDraft}
          onConfirm={handleConfirm}
        />
      ) : null}

      {!draftResponse && !confirmation ? (
        <RecentPlanningRuns
          runs={recentRuns}
          phase={recentRunsPhase}
          error={recentRunsError}
          busyRunId={busyRunId}
          onResume={handleResumeRun}
          onRetry={handleRetryRun}
          onOpen={(run) => run.mode === "EXISTING_TASK"
            ? handleOpenBoard(run.projectId)
            : handleOpenProjects(run.projectId)}
          onRefresh={loadRecentRuns}
        />
      ) : null}

      {phase === "confirming" ? (
        <p className="floating-status" role="status">
          {planningTarget
            ? "Refining the ticket and adding child tickets…"
            : "Creating your project and tickets…"}
        </p>
      ) : null}

      {confirmation ? (
        <section className="confirmation-card" aria-labelledby="confirmation-title">
          <div className="confirmation-seal" aria-hidden="true">✓</div>
          <p className="section-index">03 / Created</p>
          <h2 id="confirmation-title">
            {planningTarget ? "Ticket plan added" : "Project created"}
          </h2>
          <p className="confirmation-project">
            Project #{confirmation.projectId} · {confirmation.projectName}
          </p>
          <p className="muted-copy">
            {confirmation.taskIds.length} {planningTarget ? "child tickets are" : "tickets are"}
            {" "}now ready in your task board.
          </p>
          <div className="confirmation-actions">
            <button
              className="primary-action"
              type="button"
              onClick={() => planningTarget
                ? handleOpenBoard(confirmation.projectId)
                : handleOpenProjects(confirmation.projectId)}
            >
              <span>{planningTarget ? "Open project board" : "View project"}</span>
              <span aria-hidden="true">↗</span>
            </button>
            <button className="text-action" type="button" onClick={handleStartOver}>
              {planningTarget ? "Start a new project plan" : "Plan another project"}
            </button>
          </div>
        </section>
      ) : null}
        </>
      )}
    </main>
  );
}
