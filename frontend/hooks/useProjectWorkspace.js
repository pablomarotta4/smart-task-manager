import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { projectPermissions, ticketPermissions } from "../lib/projectPermissions";

const safeMessage = (error) => (
  error instanceof Error ? error.message : "Something unexpected happened"
);

const ACCESS_MESSAGES = Object.freeze({
  projects: {
    forbidden: "You do not have permission to view this workspace.",
    "not-found": "The requested project is no longer available.",
  },
  project: {
    forbidden: "You no longer have permission to view this project.",
    "not-found": "This project is no longer available.",
  },
  members: {
    forbidden: "You do not have permission to view project participants.",
    "not-found": "Project participants are no longer available.",
  },
  task: {
    forbidden: "You do not have permission to change this ticket.",
    "not-found": "This ticket is no longer available.",
  },
  "project-mutation": {
    forbidden: "You do not have permission to change this project.",
    "not-found": "This project is no longer available.",
  },
  "member-mutation": {
    forbidden: "You do not have permission to manage project participants.",
    "not-found": "This project participant is no longer available.",
  },
  "my-work": {
    forbidden: "You do not have permission to view this work queue.",
    "not-found": "This work queue is no longer available.",
  },
});

export const workspaceError = (error, scope) => {
  if (error?.name === "SessionChangedError") return null;
  const status = error?.status;
  const kind = status === 403
    ? "forbidden"
    : status === 404
      ? "not-found"
      : "unavailable";
  return {
    status,
    kind,
    scope,
    message: ACCESS_MESSAGES[scope]?.[kind] ?? safeMessage(error),
    retryable: kind === "unavailable",
  };
};

const missingProjectError = () => ({
  status: 404,
  kind: "not-found",
  scope: "project",
  message: ACCESS_MESSAGES.project["not-found"],
  retryable: false,
});

export default function useProjectWorkspace({
  client,
  executeAuthenticated,
  sessionKey,
  currentUserId,
}) {
  const [projects, setProjects] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [projectTasks, setProjectTasks] = useState([]);
  const [projectMembers, setProjectMembers] = useState([]);
  const [workItems, setWorkItems] = useState([]);
  const [selectedTask, setSelectedTask] = useState(null);
  const [projectPhase, setProjectPhase] = useState("idle");
  const [projectError, setProjectError] = useState(null);
  const [memberLoadPhase, setMemberLoadPhase] = useState("idle");
  const [memberLoadError, setMemberLoadError] = useState(null);
  const [projectMutationPhase, setProjectMutationPhase] = useState("idle");
  const [projectMutationError, setProjectMutationError] = useState(null);
  const [taskMutationPhase, setTaskMutationPhase] = useState("idle");
  const [taskError, setTaskError] = useState(null);
  const [memberMutationPhase, setMemberMutationPhase] = useState("idle");
  const [memberError, setMemberError] = useState(null);
  const loadRequestId = useRef(0);
  const workspaceGeneration = useRef(0);
  const projectMutationRequestId = useRef(0);
  const taskMutationRequestId = useRef(0);
  const taskOperationGenerations = useRef(new Map());
  const taskSuccessVersion = useRef(0);
  const taskSuccessOverlay = useRef(new Map());
  const memberMutationRequestId = useRef(0);
  const previousSessionKey = useRef(sessionKey);
  const projectMembersRef = useRef(projectMembers);
  const projectTasksRef = useRef(projectTasks);
  const workItemsRef = useRef(workItems);
  const selectedTaskRef = useRef(selectedTask);
  projectMembersRef.current = projectMembers;
  projectTasksRef.current = projectTasks;
  workItemsRef.current = workItems;
  selectedTaskRef.current = selectedTask;

  const resetMutationPresentation = useCallback(() => {
    setProjectMutationPhase("idle");
    setProjectMutationError(null);
    setTaskMutationPhase("idle");
    setTaskError(null);
    setMemberMutationPhase("idle");
    setMemberError(null);
  }, []);

  const beginViewNavigation = useCallback(() => {
    const requestId = loadRequestId.current + 1;
    loadRequestId.current = requestId;
    resetMutationPresentation();
    return requestId;
  }, [resetMutationPresentation]);

  const beginMutation = (requestIdRef, setPhase, phase) => {
    const generation = workspaceGeneration.current;
    const viewRequestId = loadRequestId.current;
    const mutationRequestId = requestIdRef.current + 1;
    requestIdRef.current = mutationRequestId;
    setPhase(phase);

    const isCurrent = () => (
      workspaceGeneration.current === generation
      && requestIdRef.current === mutationRequestId
    );
    return {
      isCurrent,
      isCurrentView: () => isCurrent() && loadRequestId.current === viewRequestId,
      finish: () => {
        if (isCurrent()) setPhase("idle");
      },
    };
  };

  const beginTaskMutation = (taskId, phase) => {
    const presentation = beginMutation(
      taskMutationRequestId,
      setTaskMutationPhase,
      phase,
    );
    const generation = workspaceGeneration.current;
    const viewRequestId = loadRequestId.current;
    const operationGeneration = (taskOperationGenerations.current.get(taskId) ?? 0) + 1;
    taskOperationGenerations.current.set(taskId, operationGeneration);

    const isCurrentData = () => (
      workspaceGeneration.current === generation
      && taskOperationGenerations.current.get(taskId) === operationGeneration
    );
    return {
      finish: presentation.finish,
      isCurrentData,
      isCurrentDataView: () => (
        isCurrentData() && loadRequestId.current === viewRequestId
      ),
      isCurrentPresentationView: presentation.isCurrentView,
    };
  };

  const recordTaskSuccess = (taskId, operation) => {
    const version = taskSuccessVersion.current + 1;
    taskSuccessVersion.current = version;
    taskSuccessOverlay.current.set(taskId, { ...operation, version });
  };

  const resetWorkspace = useCallback(() => {
    loadRequestId.current += 1;
    workspaceGeneration.current += 1;
    projectMutationRequestId.current += 1;
    taskMutationRequestId.current += 1;
    taskOperationGenerations.current.clear();
    taskSuccessVersion.current = 0;
    taskSuccessOverlay.current.clear();
    memberMutationRequestId.current += 1;
    setProjects([]);
    setSelectedProject(null);
    setProjectTasks([]);
    setProjectMembers([]);
    setWorkItems([]);
    setSelectedTask(null);
    setProjectPhase("idle");
    setProjectError(null);
    setMemberLoadPhase("idle");
    setMemberLoadError(null);
    resetMutationPresentation();
  }, [resetMutationPresentation]);

  useEffect(() => {
    if (previousSessionKey.current === sessionKey) return;
    previousSessionKey.current = sessionKey;
    resetWorkspace();
  }, [resetWorkspace, sessionKey]);

  const loadProjectResources = useCallback(async ({
    project,
    includeMembers,
    requestId,
  }) => {
    setProjectPhase("loading-tasks");
    setProjectError(null);
    if (includeMembers) {
      setMemberLoadPhase("loading");
      setMemberLoadError(null);
    } else {
      setMemberLoadPhase("idle");
      setMemberLoadError(null);
      setProjectMembers([]);
    }

    const tasksRequest = executeAuthenticated((token) => client.getProjectTasks({
      token,
      projectId: project.id,
    }));
    const membersRequest = includeMembers
      ? executeAuthenticated((token) => client.getProjectMembers({
        token,
        projectId: project.id,
      }))
      : Promise.resolve([]);
    const [tasksResult, membersResult] = await Promise.allSettled([
      tasksRequest,
      membersRequest,
    ]);
    if (loadRequestId.current !== requestId) return;

    if (tasksResult.status === "fulfilled") {
      setProjectTasks(tasksResult.value);
    } else {
      setProjectTasks([]);
      setProjectError(workspaceError(tasksResult.reason, "project"));
    }
    setProjectPhase("idle");

    if (includeMembers) {
      if (membersResult.status === "fulfilled") {
        setProjectMembers(membersResult.value);
      } else {
        setProjectMembers([]);
        setMemberLoadError(workspaceError(membersResult.reason, "members"));
      }
      setMemberLoadPhase("idle");
    }
  }, [client, executeAuthenticated]);

  const openProjects = useCallback(async (projectId = null) => {
    const requestId = beginViewNavigation();
    setProjectPhase("loading-projects");
    setProjectError(null);
    setMemberLoadError(null);
    setSelectedProject(null);
    setProjectTasks([]);
    setProjectMembers([]);
    setSelectedTask(null);
    try {
      const loadedProjects = await executeAuthenticated((token) => client.getProjects({ token }));
      if (loadRequestId.current !== requestId) return;
      setProjects(loadedProjects);
      if (projectId == null) {
        setProjectPhase("idle");
        return;
      }
      const project = loadedProjects.find(
        (candidate) => String(candidate.id) === String(projectId),
      );
      if (!project) {
        setProjectError(missingProjectError());
        setProjectPhase("idle");
        return;
      }
      setSelectedProject(project);
      await loadProjectResources({ project, includeMembers: false, requestId });
    } catch (error) {
      if (loadRequestId.current !== requestId) return;
      setProjects([]);
      setProjectError(workspaceError(error, "projects"));
      setProjectPhase("idle");
    }
  }, [beginViewNavigation, client, executeAuthenticated, loadProjectResources]);

  const selectProject = useCallback(async (project, { includeMembers = false } = {}) => {
    const requestId = beginViewNavigation();
    setSelectedProject(project);
    setProjectTasks([]);
    setProjectMembers([]);
    setSelectedTask(null);
    setProjectError(null);
    setMemberLoadError(null);
    await loadProjectResources({ project, includeMembers, requestId });
  }, [beginViewNavigation, loadProjectResources]);

  const openBoard = useCallback(async (projectId = null) => {
    const requestId = beginViewNavigation();
    setProjectPhase("loading-projects");
    setProjectError(null);
    setMemberLoadError(null);
    setProjectTasks([]);
    setProjectMembers([]);
    setSelectedTask(null);
    try {
      const loadedProjects = await executeAuthenticated((token) => client.getProjects({ token }));
      if (loadRequestId.current !== requestId) return;
      setProjects(loadedProjects);
      const project = projectId == null
        ? loadedProjects[0] ?? null
        : loadedProjects.find((candidate) => String(candidate.id) === String(projectId)) ?? null;
      if (projectId != null && !project) {
        setSelectedProject(null);
        setProjectError(missingProjectError());
        setProjectPhase("idle");
        return;
      }
      setSelectedProject(project);
      if (!project) {
        setProjectPhase("idle");
        return;
      }
      await loadProjectResources({ project, includeMembers: true, requestId });
    } catch (error) {
      if (loadRequestId.current !== requestId) return;
      setProjects([]);
      setSelectedProject(null);
      setProjectError(workspaceError(error, "projects"));
      setProjectPhase("idle");
    }
  }, [beginViewNavigation, client, executeAuthenticated, loadProjectResources]);

  const openMyWork = useCallback(async () => {
    const requestId = beginViewNavigation();
    const successVersionAtRequest = taskSuccessVersion.current;
    setProjectPhase("loading-tasks");
    setProjectError(null);
    setSelectedTask(null);
    setWorkItems([]);
    try {
      const assignedTasks = await executeAuthenticated((token) => client.getMyWork({ token }));
      if (loadRequestId.current !== requestId) return;
      const consumedSuccessVersion = taskSuccessVersion.current;
      const reconciledTasks = [...taskSuccessOverlay.current.entries()].reduce(
        (tasks, [taskId, operation]) => {
          if (operation.version <= successVersionAtRequest) return tasks;
          if (operation.kind === "delete") {
            return tasks.filter((task) => task.id !== taskId);
          }
          return tasks.map((task) => task.id === taskId
            ? { ...task, ...operation.task }
            : task);
        },
        assignedTasks,
      );
      setWorkItems(reconciledTasks);
      taskSuccessOverlay.current.forEach((operation, taskId) => {
        if (operation.version <= consumedSuccessVersion) {
          taskSuccessOverlay.current.delete(taskId);
        }
      });
    } catch (error) {
      if (loadRequestId.current !== requestId) return;
      setProjectError(workspaceError(error, "my-work"));
    } finally {
      if (loadRequestId.current === requestId) setProjectPhase("idle");
    }
  }, [beginViewNavigation, client, executeAuthenticated]);

  const createProject = useCallback(async (project) => {
    const mutation = beginMutation(
      projectMutationRequestId,
      setProjectMutationPhase,
      "creating",
    );
    setProjectMutationError(null);
    try {
      const created = await executeAuthenticated((token) => client.createProject({ token, project }));
      if (!mutation.isCurrent()) return false;
      setProjects((current) => [
        created,
        ...current.filter((candidate) => candidate.id !== created.id),
      ]);
      if (!mutation.isCurrentView()) return false;
      setSelectedProject(created);
      setProjectTasks([]);
      setProjectMembers([]);
      setSelectedTask(null);
      return true;
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setProjectMutationError(workspaceError(error, "project-mutation"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const updateProject = useCallback(async (projectId, projectDraft) => {
    const mutation = beginMutation(
      projectMutationRequestId,
      setProjectMutationPhase,
      "updating",
    );
    setProjectMutationError(null);
    try {
      const updated = await executeAuthenticated((token) => client.updateProject({
        token,
        projectId,
        project: projectDraft,
      }));
      if (!mutation.isCurrent()) return false;
      setProjects((current) => current.map((project) => project.id === projectId
        ? { ...project, ...updated }
        : project));
      setSelectedProject((current) => current?.id === projectId
        ? { ...current, ...updated }
        : current);
      return mutation.isCurrentView();
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setProjectMutationError(workspaceError(error, "project-mutation"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const deleteProject = useCallback(async (projectId) => {
    const mutation = beginMutation(
      projectMutationRequestId,
      setProjectMutationPhase,
      "deleting",
    );
    setProjectMutationError(null);
    try {
      await executeAuthenticated((token) => client.deleteProject({ token, projectId }));
      if (!mutation.isCurrent()) return false;
      setProjects((current) => current.filter((project) => project.id !== projectId));
      setSelectedProject((current) => current?.id === projectId ? null : current);
      setProjectTasks((current) => current.filter((task) => task.projectId !== projectId));
      setSelectedTask((current) => current?.projectId === projectId ? null : current);
      if (!mutation.isCurrentView()) return false;
      setProjectMembers([]);
      return true;
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setProjectMutationError(workspaceError(error, "project-mutation"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const updateProjectTaskCount = useCallback((projectId, delta) => {
    setProjects((current) => current.map((project) => project.id === projectId
      ? { ...project, taskCount: Math.max(0, (project.taskCount ?? 0) + delta) }
      : project));
    setSelectedProject((current) => current?.id === projectId
      ? { ...current, taskCount: Math.max(0, (current.taskCount ?? 0) + delta) }
      : current);
  }, []);

  const createTask = useCallback(async (projectId, task) => {
    const mutation = beginMutation(
      taskMutationRequestId,
      setTaskMutationPhase,
      "creating",
    );
    setTaskError(null);
    const taskRequest = { ...task, projectId };
    try {
      const created = await executeAuthenticated((token) => client.createTask({
        token,
        task: taskRequest,
      }));
      if (!mutation.isCurrent()) return false;
      const normalizedTask = {
        acceptanceCriteria: [],
        dependsOn: [],
        ...taskRequest,
        ...created,
        assigneeUsername: created.assigneeUsername === undefined
          ? projectMembersRef.current.find(
            (member) => member.userId === taskRequest.assigneeId,
          )?.username ?? null
          : created.assigneeUsername,
      };
      updateProjectTaskCount(projectId, 1);
      if (!mutation.isCurrentView()) return false;
      setProjectTasks((current) => [...current, normalizedTask]);
      return true;
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setTaskError(workspaceError(error, "task"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated, updateProjectTaskCount]);

  const updateTask = useCallback(async (taskId, taskDraft) => {
    const mutation = beginTaskMutation(taskId, "updating");
    setTaskError(null);
    const existingTask = selectedTaskRef.current?.id === taskId
      ? selectedTaskRef.current
      : projectTasksRef.current.find((task) => task.id === taskId)
        ?? workItemsRef.current.find((task) => task.id === taskId);
    const projectMembers = projectMembersRef.current;
    try {
      const updated = await executeAuthenticated((token) => client.updateTask({
        token,
        taskId,
        task: taskDraft,
      }));
      if (!mutation.isCurrentData()) return false;
      const mergedTask = {
        ...existingTask,
        ...taskDraft,
        ...updated,
        assigneeUsername: updated.assigneeUsername === undefined
          ? projectMembers.find(
            (member) => member.userId === taskDraft.assigneeId,
          )?.username
            ?? (existingTask?.assigneeId === taskDraft.assigneeId
              ? existingTask?.assigneeUsername
              : null)
          : updated.assigneeUsername,
      };
      recordTaskSuccess(taskId, { kind: "update", task: mergedTask });
      setWorkItems((current) => current.map(
        (task) => task.id === taskId ? { ...task, ...mergedTask } : task,
      ));
      if (!mutation.isCurrentDataView()) return false;
      setProjectTasks((current) => current.map(
        (task) => task.id === taskId ? mergedTask : task,
      ));
      setSelectedTask((current) => current?.id === taskId ? null : current);
      return true;
    } catch (error) {
      if (!mutation.isCurrentPresentationView()) return false;
      setTaskError(workspaceError(error, "task"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const deleteTask = useCallback(async (taskId, projectId) => {
    const mutation = beginTaskMutation(taskId, "deleting");
    setTaskError(null);
    try {
      await executeAuthenticated((token) => client.deleteTask({ token, taskId }));
      if (!mutation.isCurrentData()) return false;
      recordTaskSuccess(taskId, { kind: "delete" });
      setWorkItems((current) => current.filter((task) => task.id !== taskId));
      updateProjectTaskCount(projectId, -1);
      if (!mutation.isCurrentDataView()) return false;
      setProjectTasks((current) => current.filter((task) => task.id !== taskId));
      setSelectedTask((current) => current?.id === taskId ? null : current);
      return true;
    } catch (error) {
      if (!mutation.isCurrentPresentationView()) return false;
      setTaskError(workspaceError(error, "task"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated, updateProjectTaskCount]);

  const addMember = useCallback(async (projectId, username) => {
    const mutation = beginMutation(
      memberMutationRequestId,
      setMemberMutationPhase,
      "adding",
    );
    setMemberError(null);
    try {
      const member = await executeAuthenticated((token) => client.addProjectMember({
        token,
        projectId,
        username,
      }));
      if (!mutation.isCurrentView()) return false;
      setProjectMembers((current) => [
        ...current.filter((candidate) => candidate.userId !== member.userId),
        member,
      ]);
      return true;
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setMemberError(workspaceError(error, "member-mutation"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const removeMember = useCallback(async (projectId, member) => {
    const mutation = beginMutation(
      memberMutationRequestId,
      setMemberMutationPhase,
      "removing",
    );
    setMemberError(null);
    try {
      await executeAuthenticated((token) => client.removeProjectMember({
        token,
        projectId,
        userId: member.userId,
      }));
      if (!mutation.isCurrentView()) return false;
      setProjectMembers((current) => current.filter(
        (candidate) => candidate.userId !== member.userId,
      ));
      setProjectTasks((current) => current.map((task) => (
        task.projectId === projectId && task.assigneeId === member.userId
          ? { ...task, assigneeId: null, assigneeUsername: null }
          : task
      )));
      return true;
    } catch (error) {
      if (!mutation.isCurrentView()) return false;
      setMemberError(workspaceError(error, "member-mutation"));
      return false;
    } finally {
      mutation.finish();
    }
  }, [client, executeAuthenticated]);

  const selectTask = useCallback((task) => {
    setTaskError(null);
    setSelectedTask(task);
  }, []);
  const closeTask = useCallback(() => setSelectedTask(null), []);

  const permissions = useMemo(
    () => projectPermissions(selectedProject?.currentUserRole),
    [selectedProject?.currentUserRole],
  );
  const selectedTaskPermissions = useMemo(() => ticketPermissions({
    projectRole: selectedProject?.currentUserRole,
    task: selectedTask,
    currentUserId,
  }), [currentUserId, selectedProject?.currentUserRole, selectedTask]);

  return {
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
    permissions,
    selectedTaskPermissions,
    openProjects,
    openBoard,
    openMyWork,
    selectProject,
    selectTask,
    closeTask,
    createProject,
    updateProject,
    deleteProject,
    createTask,
    updateTask,
    deleteTask,
    addMember,
    removeMember,
    resetWorkspace,
  };
}
