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
});
