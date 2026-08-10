const DEFAULT_API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";

export class ApiError extends Error {
  constructor(message, { status, details } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

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
      });
    }

    const payload = await parseResponse(response);
    if (!response.ok) {
      throw new ApiError(
        payload?.message ?? payload?.error ?? `Request failed with status ${response.status}`,
        {
          status: response.status,
          details: payload?.details,
        },
      );
    }
    return payload;
  };

  return {
    login: (credentials) => request("/api/auth/login", { body: credentials }),
    generateProject: ({ token, prompt }) =>
      request("/api/project-generation-runs", { token, body: { prompt } }),
    confirmProject: ({ token, runId, draft }) =>
      request(`/api/project-generation-runs/${runId}/confirm`, {
        token,
        body: { draft },
      }),
    getProjects: ({ token }) => request("/api/projects", { method: "GET", token }),
    getProjectTasks: ({ token, projectId }) =>
      request(`/api/tasks/project/${encodeURIComponent(projectId)}`, {
        method: "GET",
        token,
      }),
  };
};

export const apiClient = createApiClient();
