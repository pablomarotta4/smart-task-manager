import { useState } from "react";

import { ApiError, apiClient } from "./api";
import AccountSection from "./components/AccountSection";
import BoardSection from "./components/BoardSection";
import DraftEditor from "./components/DraftEditor";
import MyWorkSection from "./components/MyWorkSection";
import ProjectsSection from "./components/ProjectsSection";

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
  const [editableDraft, setEditableDraft] = useState(null);
  const [confirmation, setConfirmation] = useState(null);
  const [activeView, setActiveView] = useState("workshop");
  const [projects, setProjects] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [projectTasks, setProjectTasks] = useState([]);
  const [projectPhase, setProjectPhase] = useState("idle");
  const [projectError, setProjectError] = useState("");
  const [projectMutationPhase, setProjectMutationPhase] = useState("idle");
  const [projectMutationError, setProjectMutationError] = useState("");
  const [selectedTask, setSelectedTask] = useState(null);
  const [savingTask, setSavingTask] = useState(false);
  const [taskError, setTaskError] = useState("");
  const [workItems, setWorkItems] = useState([]);
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
      setEditableDraft(structuredClone(response.draft));
      setConfirmation(null);
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

  const handleConfirm = async (event) => {
    event.preventDefault();
    setError("");
    setPhase("confirming");
    try {
      const response = await client.confirmProject({
        token: session.token,
        runId: draftResponse.runId,
        draft: editableDraft,
      });
      setConfirmation(response);
      setPhase("confirmed");
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      }
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
    setError("");
    setPhase("idle");
  };

  const handleProjectRequestError = (requestError) => {
    if (requestError instanceof ApiError && requestError.status === 401) {
      sessionStorage.removeItem(SESSION_KEY);
      setSession(null);
    }
    setProjectError(errorMessage(requestError));
    setProjectPhase("idle");
  };

  const handleOpenProjects = async (projectId = null) => {
    setActiveView("projects");
    setProjectError("");
    setProjectPhase("loading-projects");
    setSelectedProject(null);
    setProjectTasks([]);
    setSelectedTask(null);

    try {
      const projectsRequest = client.getProjects({ token: session.token });
      const tasksRequest = projectId === null
        ? Promise.resolve(null)
        : client.getProjectTasks({ token: session.token, projectId });
      const [loadedProjects, loadedTasks] = await Promise.all([projectsRequest, tasksRequest]);
      setProjects(loadedProjects);

      if (projectId !== null) {
        const project = loadedProjects.find((candidate) => candidate.id === projectId) ?? null;
        setSelectedProject(project);
        setProjectTasks(project ? loadedTasks : []);
      }
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handleSelectProject = async (project) => {
    setSelectedProject(project);
    setProjectTasks([]);
    setSelectedTask(null);
    setProjectError("");
    setProjectPhase("loading-tasks");
    try {
      const tasks = await client.getProjectTasks({
        token: session.token,
        projectId: project.id,
      });
      setProjectTasks(tasks);
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handleRetryProjects = () => {
    if (selectedProject) {
      handleSelectProject(selectedProject);
      return;
    }
    handleOpenProjects();
  };

  const handleCreateProject = async (project) => {
    setProjectMutationError("");
    setProjectMutationPhase("creating");
    try {
      const created = await client.createProject({ token: session.token, project });
      setProjects((current) => [created, ...current.filter(
        (candidate) => candidate.id !== created.id,
      )]);
      setSelectedProject(created);
      setProjectTasks([]);
      setSelectedTask(null);
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setProjectMutationError(errorMessage(requestError));
      }
      return false;
    } finally {
      setProjectMutationPhase("idle");
    }
  };

  const handleUpdateProject = async (project, projectDraft) => {
    setProjectMutationError("");
    setProjectMutationPhase("updating");
    try {
      const updated = await client.updateProject({
        token: session.token,
        projectId: project.id,
        project: projectDraft,
      });
      const mergedProject = { ...project, ...updated };
      setProjects((current) => current.map(
        (candidate) => candidate.id === project.id ? mergedProject : candidate,
      ));
      setSelectedProject((current) => current?.id === project.id ? mergedProject : current);
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setProjectMutationError(errorMessage(requestError));
      }
      return false;
    } finally {
      setProjectMutationPhase("idle");
    }
  };

  const handleDeleteProject = async (project) => {
    setProjectMutationError("");
    setProjectMutationPhase("deleting");
    try {
      await client.deleteProject({ token: session.token, projectId: project.id });
      setProjects((current) => current.filter((candidate) => candidate.id !== project.id));
      setSelectedProject(null);
      setProjectTasks([]);
      setSelectedTask(null);
      setActiveView("projects");
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setProjectMutationError(errorMessage(requestError));
      }
      return false;
    } finally {
      setProjectMutationPhase("idle");
    }
  };

  const handleOpenBoard = async (projectId = selectedProject?.id ?? null) => {
    setActiveView("board");
    setProjectError("");
    setProjectPhase("loading-projects");
    setProjectTasks([]);
    setSelectedTask(null);

    try {
      const loadedProjects = await client.getProjects({ token: session.token });
      setProjects(loadedProjects);
      const project = projectId === null
        ? loadedProjects[0] ?? null
        : loadedProjects.find((candidate) => candidate.id === projectId)
          ?? loadedProjects[0]
          ?? null;
      setSelectedProject(project);

      if (project) {
        setProjectPhase("loading-tasks");
        const tasks = await client.getProjectTasks({
          token: session.token,
          projectId: project.id,
        });
        setProjectTasks(tasks);
      }
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handleSaveTask = async (task, taskDraft) => {
    setSavingTask(true);
    setTaskError("");
    try {
      const updated = await client.updateTask({
        token: session.token,
        taskId: task.id,
        task: taskDraft,
      });
      const mergedTask = {
        ...task,
        title: updated.title ?? taskDraft.title,
        description: updated.description ?? taskDraft.description,
        status: updated.status ?? taskDraft.status,
        priority: updated.priority ?? taskDraft.priority,
        dueDate: updated.dueDate === undefined ? taskDraft.dueDate : updated.dueDate,
        position: updated.position ?? taskDraft.position,
      };
      setProjectTasks((current) => current.map(
        (candidate) => candidate.id === task.id ? mergedTask : candidate,
      ));
      setWorkItems((current) => current.map(
        (candidate) => candidate.id === task.id ? { ...candidate, ...mergedTask } : candidate,
      ));
      setSelectedTask(null);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setTaskError(errorMessage(requestError));
      }
    } finally {
      setSavingTask(false);
    }
  };

  const handleOpenMyWork = async () => {
    setActiveView("my-work");
    setProjectError("");
    setProjectPhase("loading-projects");
    setSelectedTask(null);
    setWorkItems([]);

    try {
      const loadedProjects = await client.getProjects({ token: session.token });
      setProjects(loadedProjects);
      setProjectPhase("loading-tasks");
      const projectBacklogs = await Promise.all(
        loadedProjects.map(async (project) => {
          const tasks = await client.getProjectTasks({
            token: session.token,
            projectId: project.id,
          });
          return tasks.map((task) => ({
            ...task,
            projectId: project.id,
            projectName: project.name,
          }));
        }),
      );
      setWorkItems(projectBacklogs.flat());
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handlePlanFollowUp = (project) => {
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

  const handleLogout = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    setActiveView("workshop");
    setProjects([]);
    setSelectedProject(null);
    setProjectTasks([]);
    setSelectedTask(null);
    setTaskError("");
    setWorkItems([]);
    setProjectError("");
    setProjectPhase("idle");
    setProjectMutationError("");
    setProjectMutationPhase("idle");
    setError("");
    setPhase("idle");
  };

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
            <h1 id="login-title">Enter the project workshop</h1>
            <p className="muted-copy">
              Sign in to turn a plain-language brief into an editable first backlog.
            </p>

            <form className="stacked-form" onSubmit={handleLogin}>
              <div className="field-group">
                <label htmlFor="username">Username</label>
                <input
                  id="username"
                  name="username"
                  autoComplete="username"
                  value={credentials.username}
                  onChange={handleCredentialChange}
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
                  autoComplete="current-password"
                  value={credentials.password}
                  onChange={handleCredentialChange}
                  placeholder="Your local test password"
                  required
                />
              </div>

              {error ? <p className="error-banner" role="alert">{error}</p> : null}
              <button
                className="primary-action"
                type="submit"
                disabled={phase === "logging-in"}
                aria-busy={phase === "logging-in"}
              >
                <span>{phase === "logging-in" ? "Signing in…" : "Enter workshop"}</span>
                <span aria-hidden="true">↗</span>
              </button>
            </form>
            <p className="local-note">Credentials stay in this browser session only.</p>
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
          <button className="text-action" type="button" onClick={handleLogout}>
            Log out <span aria-hidden="true">↗</span>
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
          onCreateProject={handleCreateProject}
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
          savingTask={savingTask}
          taskError={taskError}
          projectMutationPhase={projectMutationPhase}
          projectMutationError={projectMutationError}
          onSelectProject={handleSelectProject}
          onSelectTask={(task) => {
            setTaskError("");
            setSelectedTask(task);
          }}
          onCloseTask={() => setSelectedTask(null)}
          onSaveTask={handleSaveTask}
          onPlanFollowUp={handlePlanFollowUp}
          onUpdateProject={handleUpdateProject}
          onDeleteProject={handleDeleteProject}
          onRetry={() => handleOpenBoard(selectedProject?.id ?? null)}
        />
      ) : activeView === "my-work" ? (
        <MyWorkSection
          items={workItems}
          phase={projectPhase}
          error={projectError}
          selectedTask={selectedTask}
          savingTask={savingTask}
          taskError={taskError}
          onSelectTask={(task) => {
            setTaskError("");
            setSelectedTask(task);
          }}
          onCloseTask={() => setSelectedTask(null)}
          onSaveTask={handleSaveTask}
          onRetry={handleOpenMyWork}
        />
      ) : activeView === "account" ? (
        <AccountSection user={session.user} onLogout={handleLogout} />
      ) : (
        <>
      <section className="prompt-stage" aria-labelledby="prompt-title">
        <div className="prompt-heading">
          <p className="section-index">01 / Brief</p>
          <h1 id="prompt-title">What are we building?</h1>
          <p className="muted-copy">
            Describe the outcome in everyday language. Name the capabilities that cannot be missed.
          </p>
          <div className="prompt-principle">
            <span aria-hidden="true">✦</span>
            <p>Specific verbs make stronger tickets: list, reserve, track, schedule, approve.</p>
          </div>
        </div>

        <div className="prompt-workbench">
          <form onSubmit={handleGenerate}>
            <div className="prompt-label-row">
              <label htmlFor="project-prompt">Describe your project</label>
              <span>{prompt.length} / 4000</span>
            </div>
            <textarea
              id="project-prompt"
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              minLength={10}
              maxLength={4000}
              rows={7}
              placeholder="Build a neighborhood tool library where residents can list, reserve, borrow, and return shared tools…"
              required
            />
            <button
              className="primary-action"
              type="submit"
              disabled={phase === "generating" || prompt.trim().length < 10}
              aria-busy={phase === "generating"}
            >
              <span>{phase === "generating" ? "Generating…" : "Generate first plan"}</span>
              <span aria-hidden="true">{phase === "generating" ? "◌" : "↗"}</span>
            </button>
          </form>

          {phase === "generating" ? (
            <div className="generation-status" role="status">
              <span className="status-orbit" aria-hidden="true" />
              <div>
                <strong>Building your first backlog</strong>
                <p>Analyzing the brief, drafting tickets, and checking quality. Up to 90 seconds.</p>
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
          confirming={phase === "confirming"}
          onChange={setEditableDraft}
          onConfirm={handleConfirm}
        />
      ) : null}

      {phase === "confirming" ? (
        <p className="floating-status" role="status">Creating your project and tickets…</p>
      ) : null}

      {confirmation ? (
        <section className="confirmation-card" aria-labelledby="confirmation-title">
          <div className="confirmation-seal" aria-hidden="true">✓</div>
          <p className="section-index">03 / Created</p>
          <h2 id="confirmation-title">Project created</h2>
          <p className="confirmation-project">
            Project #{confirmation.projectId} · {confirmation.projectName}
          </p>
          <p className="muted-copy">
            {confirmation.taskIds.length} tickets are now ready in your task board.
          </p>
          <div className="confirmation-actions">
            <button
              className="primary-action"
              type="button"
              onClick={() => handleOpenProjects(confirmation.projectId)}
            >
              <span>View project</span><span aria-hidden="true">↗</span>
            </button>
            <button className="text-action" type="button" onClick={handleStartOver}>
              Plan another project
            </button>
          </div>
        </section>
      ) : null}
        </>
      )}
    </main>
  );
}
