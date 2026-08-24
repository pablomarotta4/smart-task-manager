import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "../api";
import useProjectWorkspace from "./useProjectWorkspace";

const ownerProject = {
  id: 20,
  name: "Release desk",
  currentUserRole: "OWNER",
  taskCount: 1,
};
const memberProject = {
  id: 21,
  name: "Shared launch",
  currentUserRole: "MEMBER",
  taskCount: 1,
};
const ownerTask = { id: 201, projectId: 20, title: "Ship", assigneeId: 18 };
const secondOwnerTask = { id: 204, projectId: 20, title: "Verify", assigneeId: 18 };
const memberTask = { id: 202, projectId: 21, title: "Review", assigneeId: 18 };

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
};

const createClient = () => ({
  getProjects: vi.fn().mockResolvedValue([ownerProject, memberProject]),
  getProjectTasks: vi.fn().mockResolvedValue([ownerTask]),
  getProjectMembers: vi.fn().mockResolvedValue([]),
  getProjectInvitations: vi.fn().mockResolvedValue([]),
  getMyWork: vi.fn().mockResolvedValue([memberTask]),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  deleteTask: vi.fn(),
  addProjectMember: vi.fn(),
  createProjectInvitation: vi.fn(),
  revokeProjectInvitation: vi.fn(),
  updateProjectMemberRole: vi.fn(),
  removeProjectMember: vi.fn(),
});

const renderWorkspace = ({ client = createClient(), sessionKey = 18 } = {}) => {
  const executeAuthenticated = vi.fn((operation) => operation("jwt-token"));
  const hook = renderHook(
    ({ currentSessionKey }) => useProjectWorkspace({
      client,
      executeAuthenticated,
      sessionKey: currentSessionKey,
      currentUserId: currentSessionKey,
    }),
    { initialProps: { currentSessionKey: sessionKey } },
  );
  return { ...hook, client, executeAuthenticated };
};

describe("useProjectWorkspace", () => {
  it("loads board tasks independently when member loading is forbidden", async () => {
    const client = createClient();
    client.getProjectMembers.mockRejectedValue(
      new ApiError("Forbidden", { status: 403 }),
    );
    const { result } = renderWorkspace({ client });

    await act(() => result.current.openBoard(20));

    expect(result.current.selectedProject).toEqual(ownerProject);
    expect(result.current.projectTasks).toEqual([ownerTask]);
    expect(result.current.projectError).toBeNull();
    expect(result.current.memberLoadError).toEqual(expect.objectContaining({
      status: 403,
      kind: "forbidden",
      retryable: false,
    }));
  });

  it("loads members and pending invitations for the selected board", async () => {
    const client = createClient();
    const member = { userId: 3, username: "carol", role: "MEMBER" };
    const invitation = {
      invitationId: 71,
      email: "pending@example.com",
      role: "MEMBER",
      state: "PENDING",
    };
    client.getProjectMembers.mockResolvedValue([member]);
    client.getProjectInvitations.mockResolvedValue([invitation]);
    const { result } = renderWorkspace({ client });

    await act(() => result.current.openBoard(ownerProject.id));

    expect(result.current.projectMembers).toEqual([member]);
    expect(result.current.projectInvitations).toEqual([invitation]);
    expect(client.getProjectInvitations).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: ownerProject.id,
    });
  });

  it("stores an invited person without retaining the one-time invite URL", async () => {
    const client = createClient();
    const created = {
      invitationId: 72,
      projectId: ownerProject.id,
      email: "new@example.com",
      role: "MANAGER",
      state: "PENDING",
      expiresAt: "2026-08-19T12:00:00Z",
      inviteUrl: "https://app.test/invite#token=private-token",
    };
    client.createProjectInvitation.mockResolvedValue(created);
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let response;
    await act(async () => {
      response = await result.current.inviteProjectMember(ownerProject.id, {
        email: "new@example.com",
        role: "MANAGER",
      });
    });

    expect(response).toEqual(created);
    expect(result.current.projectInvitations).toEqual([{ ...created, inviteUrl: undefined }]);
    expect(client.createProjectInvitation).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: ownerProject.id,
      email: "new@example.com",
      role: "MANAGER",
    });
  });

  it("revokes invitations and updates member roles", async () => {
    const client = createClient();
    const member = { userId: 3, username: "carol", role: "MEMBER" };
    const invitation = { invitationId: 71, email: "pending@example.com", role: "MEMBER" };
    client.getProjectMembers.mockResolvedValue([member]);
    client.getProjectInvitations.mockResolvedValue([invitation]);
    client.revokeProjectInvitation.mockResolvedValue(null);
    client.updateProjectMemberRole.mockResolvedValue({ ...member, role: "MANAGER" });
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    await act(() => result.current.revokeProjectInvitation(ownerProject.id, invitation));
    await act(() => result.current.updateMemberRole(ownerProject.id, member, "MANAGER"));

    expect(result.current.projectInvitations).toEqual([]);
    expect(result.current.projectMembers).toEqual([{ ...member, role: "MANAGER" }]);
  });

  it("does not add a delayed invitation to a newly selected project", async () => {
    const client = createClient();
    const creation = deferred();
    const otherInvitation = {
      invitationId: 81,
      email: "other@example.com",
      role: "MEMBER",
    };
    client.createProjectInvitation.mockReturnValue(creation.promise);
    client.getProjectInvitations.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === memberProject.id ? [otherInvitation] : [],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let invite;
    act(() => {
      invite = result.current.inviteProjectMember(ownerProject.id, {
        email: "old@example.com",
        role: "MEMBER",
      });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      creation.resolve({
        invitationId: 82,
        email: "old@example.com",
        role: "MEMBER",
        inviteUrl: "https://app.test/invite#token=old",
      });
      await invite;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectInvitations).toEqual([otherInvitation]);
  });

  it("does not fall back or fetch details when an explicit project is invisible", async () => {
    const { result, client } = renderWorkspace();

    await act(() => result.current.openBoard(999));

    expect(result.current.selectedProject).toBeNull();
    expect(result.current.projectError).toEqual(expect.objectContaining({
      status: 404,
      kind: "not-found",
      retryable: false,
    }));
    expect(client.getProjectTasks).not.toHaveBeenCalled();
    expect(client.getProjectMembers).not.toHaveBeenCalled();
  });

  it("suppresses a stale project response after a newer selection", async () => {
    const client = createClient();
    let resolveFirstTasks;
    client.getProjectTasks
      .mockReturnValueOnce(new Promise((resolve) => {
        resolveFirstTasks = resolve;
      }))
      .mockResolvedValueOnce([memberTask]);
    const { result } = renderWorkspace({ client });

    let firstSelection;
    act(() => {
      firstSelection = result.current.selectProject(ownerProject, { includeMembers: false });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: false }));
    await act(async () => {
      resolveFirstTasks([ownerTask]);
      await firstSelection;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
  });

  it("resets private workspace state when the session identity changes", async () => {
    const { result, rerender } = renderWorkspace();
    await act(() => result.current.openBoard(20));
    expect(result.current.projects).toHaveLength(2);

    rerender({ currentSessionKey: 99 });

    await waitFor(() => expect(result.current.projects).toEqual([]));
    expect(result.current.selectedProject).toBeNull();
    expect(result.current.projectTasks).toEqual([]);
    expect(result.current.projectMembers).toEqual([]);
    expect(result.current.workItems).toEqual([]);
  });

  it("suppresses a mutation response from a previous session", async () => {
    const client = createClient();
    let resolveCreatedProject;
    client.createProject.mockReturnValue(new Promise((resolve) => {
      resolveCreatedProject = resolve;
    }));
    const { result, rerender } = renderWorkspace({ client });

    let creation;
    act(() => {
      creation = result.current.createProject({ name: "Old session project" });
    });
    rerender({ currentSessionKey: 99 });
    await waitFor(() => expect(result.current.projectMutationPhase).toBe("idle"));

    await act(async () => {
      resolveCreatedProject({ id: 44, name: "Old session project" });
      await creation;
    });

    expect(result.current.projects).toEqual([]);
    expect(result.current.selectedProject).toBeNull();
  });

  it("classifies unavailable errors as retryable while preserving the message", async () => {
    const client = createClient();
    client.getProjects.mockRejectedValue(new ApiError("API offline"));
    const { result } = renderWorkspace({ client });

    await act(() => result.current.openProjects());

    expect(result.current.projectError).toEqual(expect.objectContaining({
      status: undefined,
      kind: "unavailable",
      message: "API offline",
      retryable: true,
    }));
  });

  it("loads My Work without fetching project data", async () => {
    const { result, client } = renderWorkspace();

    await act(() => result.current.openMyWork());

    expect(result.current.workItems).toEqual([memberTask]);
    expect(client.getMyWork).toHaveBeenCalledWith({ token: "jwt-token" });
    expect(client.getProjects).not.toHaveBeenCalled();
    expect(client.getProjectTasks).not.toHaveBeenCalled();
  });

  it("uses explicit project IDs for mutations", async () => {
    const client = createClient();
    client.addProjectMember.mockResolvedValue({
      userId: 3,
      username: "carol",
      role: "MEMBER",
    });
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(20));

    await act(() => result.current.addMember(21, "carol"));

    expect(client.addProjectMember).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 21,
      username: "carol",
    });
  });

  it("does not append a created ticket to a newly selected project", async () => {
    const client = createClient();
    const creation = deferred();
    client.createTask.mockReturnValue(creation.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let createTask;
    act(() => {
      createTask = result.current.createTask(ownerProject.id, {
        title: "Old project ticket",
        status: "TODO",
      });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      creation.resolve({ id: 203, projectId: ownerProject.id, title: "Old project ticket" });
      await createTask;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
  });

  it("does not select a newly created project after the user changed projects", async () => {
    const client = createClient();
    const creation = deferred();
    client.createProject.mockReturnValue(creation.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let createProject;
    act(() => {
      createProject = result.current.createProject({ name: "Late project" });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      creation.resolve({ id: 22, name: "Late project", currentUserRole: "OWNER" });
      await createProject;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.projects).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 22, name: "Late project" }),
    ]));
  });

  it("keeps the new selection while applying a safely keyed project update", async () => {
    const client = createClient();
    const update = deferred();
    client.updateProject.mockReturnValue(update.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let updateProject;
    act(() => {
      updateProject = result.current.updateProject(ownerProject.id, { name: "Updated release desk" });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      update.resolve({ id: ownerProject.id, name: "Updated release desk" });
      await updateProject;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.projects).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: ownerProject.id, name: "Updated release desk" }),
    ]));
  });

  it("does not close a newly selected ticket when a former project ticket update resolves", async () => {
    const client = createClient();
    const update = deferred();
    client.updateTask.mockReturnValue(update.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));
    act(() => result.current.selectTask(ownerTask));

    let updateTask;
    act(() => {
      updateTask = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Updated old ticket",
      });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    act(() => result.current.selectTask(memberTask));
    await act(async () => {
      update.resolve({ ...ownerTask, title: "Updated old ticket" });
      await updateTask;
    });

    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.selectedTask).toEqual(memberTask);
  });

  it("does not close a newly selected ticket when a former project ticket deletion resolves", async () => {
    const client = createClient();
    const deletion = deferred();
    client.deleteTask.mockReturnValue(deletion.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let deleteTask;
    act(() => {
      deleteTask = result.current.deleteTask(ownerTask.id, ownerProject.id);
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    act(() => result.current.selectTask(memberTask));
    await act(async () => {
      deletion.resolve();
      await deleteTask;
    });

    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.selectedTask).toEqual(memberTask);
  });

  it("clears a former project's pending task phase as soon as another project is selected", async () => {
    const client = createClient();
    const update = deferred();
    const memberTasks = deferred();
    client.updateTask.mockReturnValue(update.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => (
      projectId === ownerProject.id ? Promise.resolve([ownerTask]) : memberTasks.promise
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let updateTask;
    act(() => {
      updateTask = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Updated old ticket",
      });
    });
    expect(result.current.taskMutationPhase).toBe("updating");

    let selectMemberProject;
    act(() => {
      selectMemberProject = result.current.selectProject(memberProject, { includeMembers: true });
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.taskMutationPhase).toBe("idle");

    await act(async () => {
      memberTasks.resolve([memberTask]);
      await selectMemberProject;
    });
    await act(async () => {
      update.resolve({ ...ownerTask, title: "Updated old ticket" });
      await updateTask;
    });

    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.taskMutationPhase).toBe("idle");
  });

  it("clears a former project's task error when another project is selected", async () => {
    const client = createClient();
    client.updateTask.mockRejectedValue(
      new ApiError("Ticket update failed", { status: 500 }),
    );
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    await act(() => result.current.updateTask(ownerTask.id, {
      ...ownerTask,
      title: "Rejected update",
    }));
    expect(result.current.taskError).toEqual(expect.objectContaining({
      message: "Ticket update failed",
    }));

    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));

    expect(result.current.taskError).toBeNull();
  });

  it("clears mutation presentation when opening another board and fences its stale failure", async () => {
    const client = createClient();
    const update = deferred();
    const projects = deferred();
    client.updateTask.mockReturnValue(update.promise);
    client.getProjects
      .mockResolvedValueOnce([ownerProject, memberProject])
      .mockReturnValueOnce(projects.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let updateTask;
    act(() => {
      updateTask = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Rejected old update",
      });
    });
    expect(result.current.taskMutationPhase).toBe("updating");

    let openMemberBoard;
    act(() => {
      openMemberBoard = result.current.openBoard(memberProject.id);
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.taskError).toBeNull();

    await act(async () => {
      update.reject(new ApiError("Old update failed", { status: 500 }));
      await updateTask;
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.taskError).toBeNull();

    await act(async () => {
      projects.resolve([ownerProject, memberProject]);
      await openMemberBoard;
    });
    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
  });

  it("clears mutation presentation when opening projects and fences its stale failure", async () => {
    const client = createClient();
    const updateProject = deferred();
    const projects = deferred();
    client.updateTask.mockRejectedValueOnce(
      new ApiError("Previous task error", { status: 500 }),
    );
    client.updateProject.mockReturnValue(updateProject.promise);
    client.getProjects
      .mockResolvedValueOnce([ownerProject, memberProject])
      .mockReturnValueOnce(projects.promise);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));
    await act(() => result.current.updateTask(ownerTask.id, ownerTask));
    expect(result.current.taskError).toEqual(expect.objectContaining({
      message: "Previous task error",
    }));

    let pendingProjectUpdate;
    act(() => {
      pendingProjectUpdate = result.current.updateProject(ownerProject.id, {
        name: "Rejected old project update",
      });
    });
    expect(result.current.projectMutationPhase).toBe("updating");

    let openMemberProject;
    act(() => {
      openMemberProject = result.current.openProjects(memberProject.id);
    });

    expect(result.current.projectMutationPhase).toBe("idle");
    expect(result.current.projectMutationError).toBeNull();
    expect(result.current.taskError).toBeNull();

    await act(async () => {
      updateProject.reject(new ApiError("Old project update failed", { status: 500 }));
      await pendingProjectUpdate;
    });

    expect(result.current.projectMutationPhase).toBe("idle");
    expect(result.current.projectMutationError).toBeNull();
    expect(result.current.taskError).toBeNull();

    await act(async () => {
      projects.resolve([ownerProject, memberProject]);
      await openMemberProject;
    });
    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
  });

  it("clears mutation presentation when opening My Work and reconciles the stale success", async () => {
    const client = createClient();
    const deletion = deferred();
    const myWork = deferred();
    client.deleteTask.mockReturnValue(deletion.promise);
    client.getMyWork.mockReturnValue(myWork.promise);
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let deleteTask;
    act(() => {
      deleteTask = result.current.deleteTask(ownerTask.id, ownerProject.id);
    });
    expect(result.current.taskMutationPhase).toBe("deleting");

    let openMyWork;
    act(() => {
      openMyWork = result.current.openMyWork();
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.taskError).toBeNull();

    await act(async () => {
      myWork.resolve([ownerTask]);
      await openMyWork;
    });
    await act(async () => {
      deletion.resolve();
      await deleteTask;
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.taskError).toBeNull();
    expect(result.current.workItems).toEqual([]);
  });

  it("rebases a task update that completes while an older My Work request is pending", async () => {
    const client = createClient();
    const myWork = deferred();
    client.getMyWork.mockReturnValue(myWork.promise);
    client.updateTask.mockResolvedValue({ ...ownerTask, title: "Ship updated" });
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let openMyWork;
    act(() => {
      openMyWork = result.current.openMyWork();
    });
    await act(() => result.current.updateTask(ownerTask.id, {
      ...ownerTask,
      title: "Ship updated",
    }));

    await act(async () => {
      myWork.resolve([ownerTask]);
      await openMyWork;
    });

    expect(result.current.workItems).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Ship updated" }),
    ]);
  });

  it("rebases a task deletion that completes while an older My Work request is pending", async () => {
    const client = createClient();
    const myWork = deferred();
    client.getMyWork.mockReturnValue(myWork.promise);
    client.deleteTask.mockResolvedValue();
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let openMyWork;
    act(() => {
      openMyWork = result.current.openMyWork();
    });
    await act(() => result.current.deleteTask(ownerTask.id, ownerProject.id));

    await act(async () => {
      myWork.resolve([ownerTask]);
      await openMyWork;
    });

    expect(result.current.workItems).toEqual([]);
  });

  it("reconciles active My Work after a former project ticket update resolves", async () => {
    const client = createClient();
    const update = deferred();
    client.updateTask.mockReturnValue(update.promise);
    client.getMyWork.mockResolvedValue([ownerTask]);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let updateTask;
    act(() => {
      updateTask = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Updated old ticket",
      });
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(() => result.current.openMyWork());
    await act(async () => {
      update.resolve({ ...ownerTask, title: "Updated old ticket" });
      await updateTask;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.workItems).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Updated old ticket" }),
    ]);
  });

  it("reconciles active My Work after a former project ticket deletion resolves", async () => {
    const client = createClient();
    const deletion = deferred();
    client.deleteTask.mockReturnValue(deletion.promise);
    client.getMyWork.mockResolvedValue([ownerTask]);
    client.getProjectTasks.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerTask] : [memberTask],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let deleteTask;
    act(() => {
      deleteTask = result.current.deleteTask(ownerTask.id, ownerProject.id);
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(() => result.current.openMyWork());
    await act(async () => {
      deletion.resolve();
      await deleteTask;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectTasks).toEqual([memberTask]);
    expect(result.current.workItems).toEqual([]);
  });

  it("does not append an added member to a newly selected project", async () => {
    const client = createClient();
    const addition = deferred();
    const ownerMember = { userId: 3, username: "carol", role: "MEMBER" };
    const memberProjectMember = { userId: 4, username: "drew", role: "MEMBER" };
    client.addProjectMember.mockReturnValue(addition.promise);
    client.getProjectMembers.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerMember] : [memberProjectMember],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let addMember;
    act(() => {
      addMember = result.current.addMember(ownerProject.id, "new-member");
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      addition.resolve({ userId: 5, username: "new-member", role: "MEMBER" });
      await addMember;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectMembers).toEqual([memberProjectMember]);
  });

  it("does not remove a same-id member from a newly selected project", async () => {
    const client = createClient();
    const removal = deferred();
    const ownerMember = { userId: 3, username: "carol", role: "MEMBER" };
    const memberProjectMember = { userId: 3, username: "carol", role: "MEMBER" };
    client.removeProjectMember.mockReturnValue(removal.promise);
    client.getProjectMembers.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === ownerProject.id ? [ownerMember] : [memberProjectMember],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let removeMember;
    act(() => {
      removeMember = result.current.removeMember(ownerProject.id, ownerMember);
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      removal.resolve();
      await removeMember;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectMembers).toEqual([memberProjectMember]);
  });

  it("does not clear newly selected project members after deleting a former project", async () => {
    const client = createClient();
    const deletion = deferred();
    const memberProjectMember = { userId: 4, username: "drew", role: "MEMBER" };
    client.deleteProject.mockReturnValue(deletion.promise);
    client.getProjectMembers.mockImplementation(({ projectId }) => Promise.resolve(
      projectId === memberProject.id ? [memberProjectMember] : [],
    ));
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openBoard(ownerProject.id));

    let deleteProject;
    act(() => {
      deleteProject = result.current.deleteProject(ownerProject.id);
    });
    await act(() => result.current.selectProject(memberProject, { includeMembers: true }));
    await act(async () => {
      deletion.resolve();
      await deleteProject;
    });

    expect(result.current.selectedProject).toEqual(memberProject);
    expect(result.current.projectMembers).toEqual([memberProjectMember]);
  });

  it("reconciles an earlier distinct-task update while the latest mutation stays pending", async () => {
    const client = createClient();
    const firstUpdate = deferred();
    const secondUpdate = deferred();
    client.getMyWork.mockResolvedValue([ownerTask, secondOwnerTask]);
    client.getProjectTasks.mockResolvedValue([ownerTask, secondOwnerTask]);
    client.updateTask
      .mockReturnValueOnce(firstUpdate.promise)
      .mockReturnValueOnce(secondUpdate.promise);
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openMyWork());
    await act(() => result.current.openBoard(ownerProject.id));
    act(() => result.current.selectTask(secondOwnerTask));

    let updateFirstTask;
    let updateSecondTask;
    act(() => {
      updateFirstTask = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Ship updated",
      });
      updateSecondTask = result.current.updateTask(secondOwnerTask.id, {
        ...secondOwnerTask,
        title: "Verify updated",
      });
    });

    let firstResult;
    await act(async () => {
      firstUpdate.resolve({ ...ownerTask, title: "Ship updated" });
      firstResult = await updateFirstTask;
    });

    expect(firstResult).toBe(true);
    expect(result.current.taskMutationPhase).toBe("updating");
    expect(result.current.selectedTask).toEqual(secondOwnerTask);
    expect(result.current.projectTasks).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Ship updated" }),
      secondOwnerTask,
    ]);
    expect(result.current.workItems).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Ship updated" }),
      secondOwnerTask,
    ]);

    await act(async () => {
      secondUpdate.resolve({ ...secondOwnerTask, title: "Verify updated" });
      await updateSecondTask;
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.projectTasks).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Ship updated" }),
      expect.objectContaining({ id: secondOwnerTask.id, title: "Verify updated" }),
    ]);
  });

  it("reconciles an earlier distinct-task deletion while the latest mutation stays pending", async () => {
    const client = createClient();
    const firstDeletion = deferred();
    const secondUpdate = deferred();
    client.getMyWork.mockResolvedValue([ownerTask, secondOwnerTask]);
    client.getProjectTasks.mockResolvedValue([ownerTask, secondOwnerTask]);
    client.deleteTask.mockReturnValue(firstDeletion.promise);
    client.updateTask.mockReturnValue(secondUpdate.promise);
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openMyWork());
    await act(() => result.current.openBoard(ownerProject.id));

    let deleteFirstTask;
    let updateSecondTask;
    act(() => {
      deleteFirstTask = result.current.deleteTask(ownerTask.id, ownerProject.id);
      updateSecondTask = result.current.updateTask(secondOwnerTask.id, {
        ...secondOwnerTask,
        title: "Verify updated",
      });
    });

    let deletionResult;
    await act(async () => {
      firstDeletion.resolve();
      deletionResult = await deleteFirstTask;
    });

    expect(deletionResult).toBe(true);
    expect(result.current.taskMutationPhase).toBe("updating");
    expect(result.current.projectTasks).toEqual([secondOwnerTask]);
    expect(result.current.workItems).toEqual([secondOwnerTask]);

    await act(async () => {
      secondUpdate.resolve({ ...secondOwnerTask, title: "Verify updated" });
      await updateSecondTask;
    });

    expect(result.current.taskMutationPhase).toBe("idle");
    expect(result.current.projectTasks).toEqual([
      expect.objectContaining({ id: secondOwnerTask.id, title: "Verify updated" }),
    ]);
  });

  it("keeps the latest result when the same task is updated out of order", async () => {
    const client = createClient();
    const firstUpdate = deferred();
    const secondUpdate = deferred();
    client.getMyWork.mockResolvedValue([ownerTask]);
    client.updateTask
      .mockReturnValueOnce(firstUpdate.promise)
      .mockReturnValueOnce(secondUpdate.promise);
    const { result } = renderWorkspace({ client });
    await act(() => result.current.openMyWork());
    await act(() => result.current.openBoard(ownerProject.id));

    let firstMutation;
    let secondMutation;
    act(() => {
      firstMutation = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Older update",
      });
      secondMutation = result.current.updateTask(ownerTask.id, {
        ...ownerTask,
        title: "Newest update",
      });
    });
    await act(async () => {
      secondUpdate.resolve({ ...ownerTask, title: "Newest update" });
      await secondMutation;
    });
    await act(async () => {
      firstUpdate.resolve({ ...ownerTask, title: "Older update" });
      await firstMutation;
    });

    expect(result.current.projectTasks).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Newest update" }),
    ]);
    expect(result.current.workItems).toEqual([
      expect.objectContaining({ id: ownerTask.id, title: "Newest update" }),
    ]);
  });

  it("does not let an older task mutation clear the phase of a newer mutation", async () => {
    const client = createClient();
    const firstCreation = deferred();
    const secondCreation = deferred();
    client.createTask
      .mockReturnValueOnce(firstCreation.promise)
      .mockReturnValueOnce(secondCreation.promise);
    const { result } = renderWorkspace({ client });

    let firstTask;
    let secondTask;
    act(() => {
      firstTask = result.current.createTask(ownerProject.id, { title: "First", status: "TODO" });
      secondTask = result.current.createTask(memberProject.id, { title: "Second", status: "TODO" });
    });
    await act(async () => {
      firstCreation.resolve({ id: 203, projectId: ownerProject.id, title: "First" });
      await firstTask;
    });

    expect(result.current.taskMutationPhase).toBe("creating");

    await act(async () => {
      secondCreation.resolve({ id: 204, projectId: memberProject.id, title: "Second" });
      await secondTask;
    });
    expect(result.current.taskMutationPhase).toBe("idle");
  });
});
