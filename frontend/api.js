const DEFAULT_API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(message, {
    status,
    details,
    code,
    retryAfterSeconds,
    retryable = status === undefined || status === 408 || status === 425
      || status === 429 || status >= 500,
  } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
    this.retryable = retryable;
  }
}

const STABLE_ERROR_CODES = new Set([
  "ACCOUNT_ACTION_INVALID",
  "ACCOUNT_ACTION_EXPIRED",
  "ACCOUNT_ACTION_USED",
  "ACCOUNT_ACTION_SUPERSEDED",
]);

const parseRetryAfterSeconds = (response) => {
  const value = response.headers.get("retry-after");
  if (!value || !/^\d+$/.test(value)) return undefined;
  const seconds = Number(value);
  if (!Number.isSafeInteger(seconds) || seconds <= 0) return undefined;
  return Math.min(seconds, 86_400);
};

const parseResponse = async (response) => {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return null;
  }
  return response.json();
};

export const createApiClient = ({
  baseUrl = DEFAULT_API_BASE_URL,
  fetchImpl = globalThis.fetch,
} = {}) => {
  const normalizedBaseUrl = baseUrl.replace(/\/$/, "");

  const request = async (path, { method = "POST", token, body } = {}) => {
    let response;
    try {
      const requestOptions = {
        method,
        credentials: "include",
        headers: {
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      };
      response = await fetchImpl(`${normalizedBaseUrl}${path}`, requestOptions);
    } catch (error) {
      throw new ApiError("Cannot reach the Smart Task Manager API", {
        details: error instanceof Error ? error.message : String(error),
        retryable: true,
      });
    }

    const payload = await parseResponse(response);
    if (!response.ok) {
      throw new ApiError(
        payload?.message ?? payload?.error ?? `Request failed with status ${response.status}`,
        {
          status: response.status,
          details: payload?.details,
          code: STABLE_ERROR_CODES.has(payload?.code) ? payload.code : undefined,
          retryAfterSeconds: parseRetryAfterSeconds(response),
        },
      );
    }
    return payload;
  };

  return {
    login: (credentials) => request("/api/auth/login", { body: credentials }),
    register: (account) => request("/api/auth/register", { body: account }),
    getCurrentUser: ({ token }) => request("/api/auth/me", { method: "GET", token }),
    refreshSession: () => request("/api/auth/refresh"),
    logout: () => request("/api/auth/logout"),
    requestPasswordReset: ({ email }) =>
      request("/api/auth/password-reset/request", { body: { email } }),
    confirmPasswordReset: ({ token, password }) =>
      request("/api/auth/password-reset/confirm", { body: { token, password } }),
    confirmEmailVerification: ({ token }) =>
      request("/api/auth/email-verification/confirm", { body: { token } }),
    resendEmailVerification: ({ token }) =>
      request("/api/auth/email-verification/resend", { token }),
    generateProject: ({ token, prompt }) =>
      request("/api/project-generation-runs", { token, body: { prompt } }),
    generateTaskPlan: ({ token, projectId, taskId, prompt }) =>
      request(
        `/api/project-generation-runs/projects/${encodeURIComponent(projectId)}`
          + `/tasks/${encodeURIComponent(taskId)}`,
        { token, body: { prompt } },
      ),
    confirmProject: ({ token, runId, draft }) =>
      request(`/api/project-generation-runs/${runId}/confirm`, {
        token,
        body: { draft },
      }),
    getGenerationRuns: ({ token }) =>
      request("/api/project-generation-runs", { method: "GET", token }),
    getGenerationRun: ({ token, runId }) =>
      request(`/api/project-generation-runs/${encodeURIComponent(runId)}`, {
        method: "GET",
        token,
      }),
    retryGenerationRun: ({ token, runId }) =>
      request(`/api/project-generation-runs/${encodeURIComponent(runId)}/retry`, {
        method: "POST",
        token,
      }),
    getProjects: ({ token }) => request("/api/projects", { method: "GET", token }),
    createProject: ({ token, project }) =>
      request("/api/projects", { method: "POST", token, body: project }),
    updateProject: ({ token, projectId, project }) =>
      request(`/api/projects/${encodeURIComponent(projectId)}`, {
        method: "PUT",
        token,
        body: project,
      }),
    deleteProject: ({ token, projectId }) =>
      request(`/api/projects/${encodeURIComponent(projectId)}`, {
        method: "DELETE",
        token,
      }),
    getProjectMembers: ({ token, projectId }) =>
      request(`/api/projects/${encodeURIComponent(projectId)}/members`, {
        method: "GET",
        token,
      }),
    addProjectMember: ({ token, projectId, username }) =>
      request(`/api/projects/${encodeURIComponent(projectId)}/members`, {
        method: "POST",
        token,
        body: { username },
      }),
    removeProjectMember: ({ token, projectId, userId }) =>
      request(
        `/api/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(userId)}`,
        { method: "DELETE", token },
      ),
    getProjectTasks: ({ token, projectId }) =>
      request(`/api/tasks/project/${encodeURIComponent(projectId)}`, {
        method: "GET",
        token,
      }),
    getMyWork: ({ token }) => request("/api/tasks/my-work", { method: "GET", token }),
    createTask: ({ token, task }) =>
      request("/api/tasks/newtask", { method: "POST", token, body: task }),
    updateTask: ({ token, taskId, task }) =>
      request(`/api/tasks/${encodeURIComponent(taskId)}`, {
        method: "PUT",
        token,
        body: task,
      }),
    updateTaskStatus: ({ token, taskId, status }) =>
      request(
        `/api/tasks/${encodeURIComponent(taskId)}/status?status=${encodeURIComponent(status)}`,
        { method: "PATCH", token },
      ),
    assignTask: ({ token, taskId, userId }) =>
      request(
        `/api/tasks/${encodeURIComponent(taskId)}/assign${
          userId == null ? "" : `?userId=${encodeURIComponent(userId)}`
        }`,
        { method: "PATCH", token },
      ),
    deleteTask: ({ token, taskId }) =>
      request(`/api/tasks/${encodeURIComponent(taskId)}`, {
        method: "DELETE",
        token,
      }),
  };
};

export const apiClient = createApiClient();
