import { describe, expect, it, vi } from "vitest";

import { ApiError, createApiClient } from "./api";

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

describe("project API client", () => {
  it("logs in with the supplied credentials", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse({ token: "jwt-token", user: { username: "pablo-local" } }),
    );
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    const response = await client.login({
      username: "pablo-local",
      password: "secret",
    });

    expect(response.token).toBe("jwt-token");
    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/auth/login",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ username: "pablo-local", password: "secret" }),
      }),
    );
  });

  it("generates a project with bearer authentication", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ runId: "run-1" }, 201));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.generateProject({ token: "jwt-token", prompt: "Build a meal planner" });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/project-generation-runs",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
        body: JSON.stringify({ prompt: "Build a meal planner" }),
      }),
    );
  });

  it("confirms the edited draft for the generated run", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ projectId: 42 }, 201));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });
    const draft = { name: "Edited project", tickets: [] };

    await client.confirmProject({ token: "jwt-token", runId: "run-1", draft });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/project-generation-runs/run-1/confirm",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ draft }),
      }),
    );
  });

  it("normalizes Spring error responses", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          message: "AI planning service is unavailable",
          details: "uri=/api/project-generation-runs",
        },
        502,
      ),
    );
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await expect(
      client.generateProject({ token: "jwt-token", prompt: "Build a meal planner" }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: "ApiError",
        message: "AI planning service is unavailable",
        status: 502,
        details: "uri=/api/project-generation-runs",
      }),
    );
    expect(ApiError.prototype).toBeInstanceOf(Error);
  });

  it("loads project summaries with bearer authentication", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse([{ id: 20 }]));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.getProjects({ token: "jwt-token" });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/projects",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
      }),
    );
    expect(fetchImpl.mock.calls[0][1]).not.toHaveProperty("body");
  });

  it("creates and updates a project without an AI run", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ id: 21 }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: 21 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });
    const project = { name: "Release checklist", objective: "Ship with confidence" };

    await client.createProject({ token: "jwt-token", project });
    await client.updateProject({ token: "jwt-token", projectId: 21, project });

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "http://api.test/api/projects",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(project),
      }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "http://api.test/api/projects/21",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify(project),
      }),
    );
  });

  it("deletes a project with bearer authentication", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.deleteProject({ token: "jwt-token", projectId: "release/21" });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/projects/release%2F21",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
      }),
    );
  });

  it("loads the selected project's tickets", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse([{ id: 101 }]));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.getProjectTasks({ token: "jwt-token", projectId: 20 });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/tasks/project/20",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
      }),
    );
  });

  it("updates the editable fields for a saved ticket", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ id: 201 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });
    const task = {
      title: "Prepare interview notes",
      description: "Collect role context and questions for the interview panel.",
      status: "IN_PROGRESS",
      projectId: 20,
      priority: "HIGH",
      category: "Interviews",
      dueDate: "2026-08-18",
      position: 2,
    };

    await client.updateTask({ token: "jwt-token", taskId: 201, task });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/tasks/201",
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
        body: JSON.stringify(task),
      }),
    );
  });

  it("creates and deletes a manual ticket", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ id: 202 }, 201))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });
    const task = {
      title: "Prepare release notes",
      description: "Summarize the user-visible changes for this release.",
      status: "TODO",
      projectId: 21,
      priority: "MEDIUM",
      category: "Release",
      dueDate: null,
      position: 0,
    };

    await client.createTask({ token: "jwt-token", task });
    await client.deleteTask({ token: "jwt-token", taskId: 202 });

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "http://api.test/api/tasks/newtask",
      expect.objectContaining({ method: "POST", body: JSON.stringify(task) }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "http://api.test/api/tasks/202",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("moves a ticket to a new execution status", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ id: 201, status: "DONE" }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.updateTaskStatus({
      token: "jwt-token",
      taskId: 201,
      status: "DONE",
    });

    expect(fetchImpl).toHaveBeenCalledWith(
      "http://api.test/api/tasks/201/status?status=DONE",
      expect.objectContaining({
        method: "PATCH",
        headers: expect.objectContaining({ Authorization: "Bearer jwt-token" }),
      }),
    );
    expect(fetchImpl.mock.calls[0][1]).not.toHaveProperty("body");
  });

  it("manages project participation without listing global users", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{ userId: 1, username: "alice" }]))
      .mockResolvedValueOnce(jsonResponse({ userId: 2, username: "bob" }, 201))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.getProjectMembers({ token: "jwt-token", projectId: "team/20" });
    await client.addProjectMember({
      token: "jwt-token",
      projectId: "team/20",
      username: "bob",
    });
    await client.removeProjectMember({
      token: "jwt-token",
      projectId: "team/20",
      userId: "user/2",
    });

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "http://api.test/api/projects/team%2F20/members",
      expect.objectContaining({ method: "GET" }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "http://api.test/api/projects/team%2F20/members",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ username: "bob" }) }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      3,
      "http://api.test/api/projects/team%2F20/members/user%2F2",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("loads the principal queue and assigns tickets", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{ id: 201, assigneeUsername: "alice" }]))
      .mockResolvedValueOnce(jsonResponse({ id: 201, assigneeId: 2 }));
    const client = createApiClient({ baseUrl: "http://api.test", fetchImpl });

    await client.getMyWork({ token: "jwt-token" });
    await client.assignTask({ token: "jwt-token", taskId: "task/201", userId: "user/2" });

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "http://api.test/api/tasks/my-work",
      expect.objectContaining({ method: "GET" }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "http://api.test/api/tasks/task%2F201/assign?userId=user%2F2",
      expect.objectContaining({ method: "PATCH" }),
    );
  });
});
