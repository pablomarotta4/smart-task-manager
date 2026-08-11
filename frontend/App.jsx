import { useCallback, useEffect, useState } from "react";

import { ApiError, apiClient } from "./api";
import AccountSection from "./components/AccountSection";
import BoardSection from "./components/BoardSection";
import DraftEditor from "./components/DraftEditor";
import MyWorkSection from "./components/MyWorkSection";
import ProjectsSection from "./components/ProjectsSection";
import RecentPlanningRuns from "./components/RecentPlanningRuns";

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
  const [planningTarget, setPlanningTarget] = useState(null);
  const [activeView, setActiveView] = useState("workshop");
  const [projects, setProjects] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [projectTasks, setProjectTasks] = useState([]);
  const [projectMembers, setProjectMembers] = useState([]);
  const [projectPhase, setProjectPhase] = useState("idle");
  const [projectError, setProjectError] = useState("");
  const [projectMutationPhase, setProjectMutationPhase] = useState("idle");
  const [projectMutationError, setProjectMutationError] = useState("");
  const [selectedTask, setSelectedTask] = useState(null);
  const [taskMutationPhase, setTaskMutationPhase] = useState("idle");
  const [taskError, setTaskError] = useState("");
  const [memberMutationPhase, setMemberMutationPhase] = useState("idle");
  const [memberError, setMemberError] = useState("");
  const [workItems, setWorkItems] = useState([]);
  const [phase, setPhase] = useState("idle");
  const [error, setError] = useState("");
  const [recentRuns, setRecentRuns] = useState([]);
  const [recentRunsPhase, setRecentRunsPhase] = useState("idle");
  const [recentRunsError, setRecentRunsError] = useState("");
  const [busyRunId, setBusyRunId] = useState(null);

  const sessionToken = session?.token;
  const loadRecentRuns = useCallback(async () => {
    if (!sessionToken) {
      setRecentRuns([]);
      return;
    }
    setRecentRunsError("");
    setRecentRunsPhase("loading");
    try {
      const runs = await client.getGenerationRuns({ token: sessionToken });
      setRecentRuns(runs);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setRecentRunsError(errorMessage(requestError));
      }
    } finally {
      setRecentRunsPhase("idle");
    }
  }, [client, sessionToken]);

  useEffect(() => {
    void loadRecentRuns();
  }, [loadRecentRuns]);

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
      const generationRequest = {
        token: session.token,
        prompt: prompt.trim(),
      };
      const response = planningTarget
        ? await client.generateTaskPlan({
          ...generationRequest,
          projectId: planningTarget.project.id,
          taskId: planningTarget.task.id,
        })
        : await client.generateProject(generationRequest);
      setDraftResponse(response);
      setEditableDraft(structuredClone(response.draft));
      setConfirmation(null);
      setPhase("reviewing");
      void loadRecentRuns();
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
      void loadRecentRuns();
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
    setPlanningTarget(null);
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
    setMemberError("");
    setProjectPhase("loading-projects");
    setSelectedProject(null);
    setProjectTasks([]);
    setProjectMembers([]);
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
    setProjectMembers([]);
    setSelectedTask(null);
    setProjectError("");
    setMemberError("");
    setProjectPhase("loading-tasks");
    try {
      const [tasks, members] = await Promise.all([
        client.getProjectTasks({ token: session.token, projectId: project.id }),
        client.getProjectMembers({ token: session.token, projectId: project.id }),
      ]);
      setProjectTasks(tasks);
      setProjectMembers(members);
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
      setProjectMembers([]);
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
      setProjectMembers([]);
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
    setMemberError("");
    setProjectPhase("loading-projects");
    setProjectTasks([]);
    setProjectMembers([]);
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
        const [tasks, members] = await Promise.all([
          client.getProjectTasks({ token: session.token, projectId: project.id }),
          client.getProjectMembers({ token: session.token, projectId: project.id }),
        ]);
        setProjectTasks(tasks);
        setProjectMembers(members);
      }
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handleSaveTask = async (task, taskDraft) => {
    setTaskMutationPhase("updating");
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
        assigneeId: updated.assigneeId === undefined ? taskDraft.assigneeId : updated.assigneeId,
        assigneeUsername: updated.assigneeUsername === undefined
          ? projectMembers.find((member) => member.userId === taskDraft.assigneeId)?.username
            ?? (task.assigneeId === taskDraft.assigneeId ? task.assigneeUsername : null)
          : updated.assigneeUsername,
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
      setTaskMutationPhase("idle");
    }
  };

  const updateProjectTaskCount = (projectId, delta) => {
    setProjects((current) => current.map((project) => project.id === projectId
      ? { ...project, taskCount: Math.max(0, (project.taskCount ?? 0) + delta) }
      : project));
    setSelectedProject((current) => current?.id === projectId
      ? { ...current, taskCount: Math.max(0, (current.taskCount ?? 0) + delta) }
      : current);
  };

  const handleCreateTask = async (task) => {
    setTaskError("");
    setTaskMutationPhase("creating");
    try {
      const created = await client.createTask({ token: session.token, task });
      const normalizedTask = {
        acceptanceCriteria: [],
        dependsOn: [],
        ...task,
        ...created,
        assigneeUsername: created.assigneeUsername === undefined
          ? projectMembers.find((member) => member.userId === task.assigneeId)?.username ?? null
          : created.assigneeUsername,
      };
      setProjectTasks((current) => [...current, normalizedTask]);
      updateProjectTaskCount(task.projectId, 1);
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setTaskError(errorMessage(requestError));
      }
      return false;
    } finally {
      setTaskMutationPhase("idle");
    }
  };

  const handleDeleteTask = async (task) => {
    setTaskError("");
    setTaskMutationPhase("deleting");
    try {
      await client.deleteTask({ token: session.token, taskId: task.id });
      setProjectTasks((current) => current.filter((candidate) => candidate.id !== task.id));
      setWorkItems((current) => current.filter((candidate) => candidate.id !== task.id));
      updateProjectTaskCount(task.projectId, -1);
      setSelectedTask(null);
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setTaskError(errorMessage(requestError));
      }
      return false;
    } finally {
      setTaskMutationPhase("idle");
    }
  };

  const handleOpenMyWork = async () => {
    setActiveView("my-work");
    setProjectError("");
    setProjectPhase("loading-tasks");
    setSelectedTask(null);
    setWorkItems([]);

    try {
      const assignedTasks = await client.getMyWork({ token: session.token });
      setWorkItems(assignedTasks);
      setProjectPhase("idle");
    } catch (requestError) {
      handleProjectRequestError(requestError);
    }
  };

  const handleAddProjectMember = async (username) => {
    setMemberError("");
    setMemberMutationPhase("adding");
    try {
      const member = await client.addProjectMember({
        token: session.token,
        projectId: selectedProject.id,
        username,
      });
      setProjectMembers((current) => [
        ...current.filter((candidate) => candidate.userId !== member.userId),
        member,
      ]);
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setMemberError(errorMessage(requestError));
      }
      return false;
    } finally {
      setMemberMutationPhase("idle");
    }
  };

  const handleRemoveProjectMember = async (member) => {
    setMemberError("");
    setMemberMutationPhase("removing");
    try {
      await client.removeProjectMember({
        token: session.token,
        projectId: selectedProject.id,
        userId: member.userId,
      });
      setProjectMembers((current) => current.filter(
        (candidate) => candidate.userId !== member.userId,
      ));
      setProjectTasks((current) => current.map((task) => task.assigneeId === member.userId
        ? { ...task, assigneeId: null, assigneeUsername: null }
        : task));
      return true;
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setMemberError(errorMessage(requestError));
      }
      return false;
    } finally {
      setMemberMutationPhase("idle");
    }
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
    setSelectedTask(null);
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
      const response = await client.getGenerationRun({
        token: session.token,
        runId: run.runId,
      });
      restoreDraft(response, response);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
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
      const response = await client.retryGenerationRun({
        token: session.token,
        runId: run.runId,
      });
      restoreDraft(response, run);
      void loadRecentRuns();
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        sessionStorage.removeItem(SESSION_KEY);
        setSession(null);
      } else {
        setError(errorMessage(requestError));
        setPhase("idle");
        void loadRecentRuns();
      }
    } finally {
      setBusyRunId(null);
    }
  };

  const handleLogout = () => {
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
    setDraftResponse(null);
    setEditableDraft(null);
    setConfirmation(null);
    setPlanningTarget(null);
    setActiveView("workshop");
    setProjects([]);
    setSelectedProject(null);
    setProjectTasks([]);
    setProjectMembers([]);
    setSelectedTask(null);
    setTaskError("");
    setMemberError("");
    setMemberMutationPhase("idle");
    setTaskMutationPhase("idle");
    setWorkItems([]);
    setRecentRuns([]);
    setRecentRunsError("");
    setRecentRunsPhase("idle");
    setBusyRunId(null);
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
          savingTask={taskMutationPhase === "updating"}
          taskError={taskError}
          taskMutationPhase={taskMutationPhase}
          projectMembers={projectMembers}
          memberMutationPhase={memberMutationPhase}
          memberError={memberError}
          projectMutationPhase={projectMutationPhase}
          projectMutationError={projectMutationError}
          onSelectProject={handleSelectProject}
          onSelectTask={(task) => {
            setTaskError("");
            setSelectedTask(task);
          }}
          onCloseTask={() => setSelectedTask(null)}
          onSaveTask={handleSaveTask}
          onCreateTask={handleCreateTask}
          onDeleteTask={handleDeleteTask}
          onAddProjectMember={handleAddProjectMember}
          onRemoveProjectMember={handleRemoveProjectMember}
          onPlanTask={handlePlanTask}
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
          savingTask={taskMutationPhase === "updating"}
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
              minLength={10}
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
              disabled={phase === "generating" || prompt.trim().length < 10}
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
