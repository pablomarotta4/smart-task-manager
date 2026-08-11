import { act } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import { ApiError } from "./api";

const authenticatedUser = {
  token: "jwt-token",
  user: {
    id: 18,
    username: "pablo-local",
    fullName: "Pablo Local Tester",
  },
};

const generatedDraft = {
  runId: "run-1",
  status: "DRAFT_READY",
  draft: {
    name: "Kitchen Redesign Project",
    objective: "Plan and deliver a practical kitchen redesign within the agreed budget.",
    assumptions: ["The existing kitchen footprint stays unchanged"],
    risks: ["Contractor availability may affect the schedule"],
    tickets: [
      {
        client_id: "design-kitchen",
        title: "Define the kitchen redesign requirements",
        description: "Document the desired layout, appliances, materials, and constraints.",
        priority: "HIGH",
        estimated_hours: 6,
        acceptance_criteria: ["A reviewed requirements document lists every requested change"],
        depends_on: [],
        category: "Planning",
        due_in_days: 3,
      },
      {
        client_id: "select-contractors",
        title: "Select qualified renovation contractors",
        description: "Compare qualified contractors using scope, price, and availability.",
        priority: "HIGH",
        estimated_hours: 8,
        acceptance_criteria: ["At least three comparable contractor proposals are evaluated"],
        depends_on: ["design-kitchen"],
        category: "Procurement",
        due_in_days: 10,
      },
      {
        client_id: "schedule-work",
        title: "Schedule the renovation work",
        description: "Create a sequenced schedule for demolition, installation, and inspection.",
        priority: "MEDIUM",
        estimated_hours: 4,
        acceptance_criteria: ["Every work phase has an owner and target completion date"],
        depends_on: ["select-contractors"],
        category: "Delivery",
        due_in_days: 14,
      },
    ],
  },
  quality: {
    score: 100,
    passed: true,
    issues: [],
    metrics: {
      ticket_count: 3,
      unique_title_ratio: 1,
      max_title_similarity: 0.2,
      description_coverage: 1,
      acceptance_criteria_coverage: 1,
    },
  },
  revisionCount: 0,
  model: "gemma3:4b",
};

const savedProjects = [
  {
    id: 20,
    name: "Job Application Tracker - Initial Backlog",
    objective: "Track opportunities from discovery through offer decisions.",
    taskCount: 6,
    ownerId: 18,
    ownerUsername: "pablo-local",
    createdAt: "2026-08-09T23:57:01.424559",
  },
  {
    id: 19,
    name: "Neighborhood Tool Lending Library - Phase 1",
    objective: "Let neighbors reserve and return shared tools.",
    taskCount: 6,
    ownerId: 18,
    ownerUsername: "pablo-local",
    createdAt: "2026-08-09T23:44:04.410638",
  },
];

const savedProjectTasks = [
  {
    id: 201,
    projectId: 20,
    title: "Create opportunity intake",
    description: "Capture company, role, source, compensation, and the application link.",
    status: "TODO",
    position: 0,
    priority: "HIGH",
    category: "Opportunities",
    dueDate: "2026-08-14",
    estimatedHours: 4.5,
    planningClientId: "opportunity-intake",
    acceptanceCriteria: ["A saved opportunity includes company, role, and source"],
    dependsOn: [],
    aiSummary: "Build the structured intake for every potential role.",
  },
  {
    id: 202,
    projectId: 20,
    title: "Track application stages",
    description: "Show every application in its current stage with the next action.",
    status: "IN_PROGRESS",
    position: 1,
    priority: "HIGH",
    category: "Workflow",
    dueDate: "2026-08-18",
    estimatedHours: 6,
    planningClientId: "application-stages",
    acceptanceCriteria: ["Every application has a visible current stage"],
    dependsOn: ["opportunity-intake"],
    aiSummary: "Make application progress and next actions visible.",
  },
];

const savedOtherProjectTasks = [
  {
    id: 301,
    projectId: 19,
    title: "Repair the reservation handoff",
    description: "Remove the blocker between tool availability and confirmed reservations.",
    status: "BLOCKED",
    position: 0,
    priority: "URGENT",
    category: "Reservations",
    dueDate: "2026-08-11",
    estimatedHours: 3,
    planningClientId: "reservation-handoff",
    acceptanceCriteria: ["A resident can reserve an available tool"],
    dependsOn: ["tool-inventory"],
    aiSummary: "Restore the critical reservation path.",
  },
  {
    id: 302,
    projectId: 19,
    title: "Catalog the remaining hand tools",
    description: "Add the lower-priority inventory that has no target date yet.",
    status: "TODO",
    position: 1,
    priority: "LOW",
    category: "Inventory",
    dueDate: null,
    estimatedHours: 2,
    planningClientId: "remaining-hand-tools",
    acceptanceCriteria: ["Every available hand tool appears in the catalog"],
    dependsOn: [],
    aiSummary: "Complete the long-tail inventory after the critical path.",
  },
  {
    id: 303,
    projectId: 19,
    title: "Reconcile the inventory audit",
    description: "Close the inventory audit that passed its target date.",
    status: "TODO",
    position: 2,
    priority: "LOW",
    category: "Inventory",
    dueDate: "2000-01-01",
    estimatedHours: 1,
    planningClientId: "overdue-inventory-audit",
    acceptanceCriteria: ["The audit result matches the physical inventory"],
    dependsOn: [],
    aiSummary: "Surface an overdue task without promoting its priority.",
  },
];

const projectMembers = [
  {
    membershipId: 401,
    userId: 18,
    username: "pablo-local",
    fullName: "Pablo Local Tester",
    owner: true,
    joinedAt: "2026-08-09T12:00:00",
  },
  {
    membershipId: 402,
    userId: 2,
    username: "bob",
    fullName: "Bob Builder",
    owner: false,
    joinedAt: "2026-08-10T12:00:00",
  },
];

const assignedWorkItems = [
  {
    ...savedProjectTasks[1],
    projectName: savedProjects[0].name,
    assigneeId: 18,
    assigneeUsername: "pablo-local",
  },
  {
    ...savedOtherProjectTasks[0],
    projectName: savedProjects[1].name,
    assigneeId: 18,
    assigneeUsername: "pablo-local",
  },
  {
    ...savedOtherProjectTasks[1],
    projectName: savedProjects[1].name,
    assigneeId: 18,
    assigneeUsername: "pablo-local",
  },
  {
    ...savedOtherProjectTasks[2],
    projectName: savedProjects[1].name,
    assigneeId: 18,
    assigneeUsername: "pablo-local",
  },
];

const readyPlanningRun = {
  runId: "run-ready",
  mode: "NEW_PROJECT",
  status: "DRAFT_READY",
  prompt: "Build a home renovation plan for redesigning and delivering a new kitchen",
  attemptCount: 1,
  projectId: null,
  projectName: null,
  targetTaskId: null,
  targetTaskTitle: null,
  errorCode: null,
  retryable: false,
  createdAt: "2026-08-11T10:00:00",
  updatedAt: "2026-08-11T10:01:00",
};

const failedExistingPlanningRun = {
  runId: "run-failed",
  mode: "EXISTING_TASK",
  status: "FAILED",
  prompt: "Break this ticket into an actionable implementation plan",
  attemptCount: 1,
  projectId: 20,
  projectName: "Job Application Tracker - Initial Backlog",
  targetTaskId: 201,
  targetTaskTitle: "Create opportunity intake",
  errorCode: "AI_PLANNING_UNAVAILABLE",
  retryable: true,
  createdAt: "2026-08-11T10:00:00",
  updatedAt: "2026-08-11T10:01:00",
};

const createClient = () => ({
  login: vi.fn().mockResolvedValue(authenticatedUser),
  generateProject: vi.fn().mockResolvedValue(generatedDraft),
  generateTaskPlan: vi.fn().mockResolvedValue(generatedDraft),
  confirmProject: vi.fn(),
  getGenerationRuns: vi.fn().mockResolvedValue([]),
  getGenerationRun: vi.fn(),
  retryGenerationRun: vi.fn(),
  getProjects: vi.fn().mockResolvedValue(savedProjects),
  createProject: vi.fn().mockImplementation(({ project }) => Promise.resolve({
    id: 21,
    ...project,
    ownerId: 7,
    ownerUsername: "pablo-local",
    taskCount: 0,
    createdAt: "2026-08-11T12:00:00",
  })),
  updateProject: vi.fn().mockImplementation(({ projectId, project }) =>
    Promise.resolve({ ...savedProjects[0], id: projectId, ...project })),
  deleteProject: vi.fn().mockResolvedValue(null),
  getProjectMembers: vi.fn().mockResolvedValue(projectMembers),
  addProjectMember: vi.fn().mockImplementation(({ username }) => Promise.resolve({
    membershipId: 403,
    userId: 3,
    username,
    fullName: "Carol Coordinator",
    owner: false,
    joinedAt: "2026-08-11T12:00:00",
  })),
  removeProjectMember: vi.fn().mockResolvedValue(null),
  getProjectTasks: vi.fn().mockResolvedValue(savedProjectTasks),
  getMyWork: vi.fn().mockResolvedValue(assignedWorkItems),
  createTask: vi.fn().mockImplementation(({ task }) =>
    Promise.resolve({ id: 203, ...task })),
  updateTask: vi.fn().mockImplementation(({ taskId, task }) =>
    Promise.resolve({ id: taskId, ...task })),
  updateTaskStatus: vi.fn().mockImplementation(({ taskId, status }) =>
    Promise.resolve({ id: taskId, status })),
  assignTask: vi.fn().mockImplementation(({ taskId, userId }) =>
    Promise.resolve({ id: taskId, assigneeId: userId })),
  deleteTask: vi.fn().mockResolvedValue(null),
});

const logIn = async (user, client) => {
  await user.type(screen.getByLabelText(/username/i), "pablo-local");
  await user.type(screen.getByLabelText(/password/i), "SmartTasks123!");
  await user.click(screen.getByRole("button", { name: /enter workshop/i }));
  await screen.findByText("Pablo Local Tester");
  expect(client.login).toHaveBeenCalledWith({
    username: "pablo-local",
    password: "SmartTasks123!",
  });
};

const generateDraft = async (user, client) => {
  await logIn(user, client);
  await user.type(
    screen.getByLabelText(/describe your project/i),
    "Build a home renovation plan for redesigning and delivering a new kitchen",
  );
  await user.click(screen.getByRole("button", { name: /generate first plan/i }));
  await screen.findByRole("heading", { name: "Kitchen Redesign Project" });
};

describe("AI project workshop", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("signs in and opens the prompt workspace", async () => {
    const user = userEvent.setup();
    const client = createClient();

    render(<App client={client} />);
    await logIn(user, client);

    expect(screen.getByRole("heading", { name: /what are we building/i })).toBeInTheDocument();
    expect(sessionStorage.getItem("smart-task-session")).toContain("jwt-token");
  });

  it("shows honest progress while generating and then displays the draft", async () => {
    const user = userEvent.setup();
    let resolveGeneration;
    const client = createClient();
    client.generateProject.mockReturnValue(
      new Promise((resolve) => {
        resolveGeneration = resolve;
      }),
    );
    render(<App client={client} />);
    await logIn(user, client);

    const prompt = "Build a home renovation plan for redesigning and delivering a new kitchen";
    await user.type(screen.getByLabelText(/describe your project/i), prompt);
    await user.click(screen.getByRole("button", { name: /generate first plan/i }));

    expect(screen.getByRole("status")).toHaveTextContent(/building your first backlog/i);
    expect(screen.getByRole("button", { name: /generating/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /generating/i })).toHaveAttribute(
      "aria-busy",
      "true",
    );

    await act(async () => {
      resolveGeneration(generatedDraft);
    });

    expect(await screen.findByRole("heading", { name: "Kitchen Redesign Project" })).toBeInTheDocument();
    expect(client.generateProject).toHaveBeenCalledWith({ token: "jwt-token", prompt });
  });

  it("keeps the prompt available when generation fails", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.generateProject.mockRejectedValue(
      new ApiError("AI planning service is unavailable", { status: 502 }),
    );
    render(<App client={client} />);
    await logIn(user, client);

    const prompt = "Build a home renovation plan for redesigning and delivering a new kitchen";
    const promptField = screen.getByLabelText(/describe your project/i);
    await user.type(promptField, prompt);
    await user.click(screen.getByRole("button", { name: /generate first plan/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("AI planning service is unavailable");
    expect(promptField).toHaveValue(prompt);
  });

  it("restores a persisted draft after an authenticated page refresh", async () => {
    const user = userEvent.setup();
    const client = createClient();
    sessionStorage.setItem("smart-task-session", JSON.stringify(authenticatedUser));
    client.getGenerationRuns.mockResolvedValue([readyPlanningRun]);
    client.getGenerationRun.mockResolvedValue({
      ...readyPlanningRun,
      ...generatedDraft,
      runId: readyPlanningRun.runId,
    });

    render(<App client={client} />);

    expect(await screen.findByRole("heading", { name: /recent ai plans/i }))
      .toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /resume draft/i }));

    expect(client.getGenerationRun).toHaveBeenCalledWith({
      token: "jwt-token",
      runId: "run-ready",
    });
    expect(await screen.findByRole("heading", { name: "Kitchen Redesign Project" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText(/describe your project/i)).toHaveValue(
      readyPlanningRun.prompt,
    );
  });

  it("retries a failed existing-ticket run with its saved planning context", async () => {
    const user = userEvent.setup();
    const client = createClient();
    sessionStorage.setItem("smart-task-session", JSON.stringify(authenticatedUser));
    client.getGenerationRuns.mockResolvedValue([failedExistingPlanningRun]);
    client.retryGenerationRun.mockResolvedValue({
      ...generatedDraft,
      runId: failedExistingPlanningRun.runId,
    });

    render(<App client={client} />);

    await user.click(await screen.findByRole("button", { name: /retry plan/i }));

    expect(client.retryGenerationRun).toHaveBeenCalledWith({
      token: "jwt-token",
      runId: "run-failed",
    });
    expect(await screen.findByLabelText(/refined ticket title/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/planning target/i)).toHaveTextContent(
      "Job Application Tracker - Initial Backlog",
    );
    expect(screen.getByLabelText(/planning target/i)).toHaveTextContent(
      "Create opportunity intake",
    );
  });

  it("keeps the Workshop usable when recent planning history cannot load", async () => {
    const user = userEvent.setup();
    const client = createClient();
    sessionStorage.setItem("smart-task-session", JSON.stringify(authenticatedUser));
    client.getGenerationRuns.mockRejectedValueOnce(
      new ApiError("Could not load recent plans", { status: 500 }),
    );

    render(<App client={client} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load recent plans");
    expect(screen.getByRole("heading", { name: /what are we building/i }))
      .toBeInTheDocument();
    client.getGenerationRuns.mockResolvedValue([]);
    await user.click(screen.getByRole("button", { name: /try again/i }));
    expect(await screen.findByText(/no saved ai plans yet/i)).toBeInTheDocument();
  });

  it("shows quality evidence and lets the user edit ticket content", async () => {
    const user = userEvent.setup();
    const client = createClient();
    const weakDraft = structuredClone(generatedDraft);
    weakDraft.quality = {
      ...weakDraft.quality,
      score: 65,
      passed: false,
      issues: [
        {
          code: "missing_explicit_capabilities",
          message: "Tickets must explicitly implement contractor selection",
          ticket_ids: [],
        },
      ],
    };
    client.generateProject.mockResolvedValue(weakDraft);
    render(<App client={client} />);

    await generateDraft(user, client);

    expect(screen.getByText(/needs attention/i)).toBeInTheDocument();
    expect(screen.getByText("65 / 100")).toBeInTheDocument();
    expect(screen.getByRole("status", { name: /plan quality/i })).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: /quality score/i })).toHaveAttribute(
      "aria-valuenow",
      "65",
    );
    expect(
      screen.getByText(/tickets must explicitly implement contractor selection/i),
    ).toBeInTheDocument();

    const firstTitle = screen.getByLabelText(/title for ticket 1/i);
    await user.clear(firstTitle);
    await user.type(firstTitle, "Document kitchen requirements with the homeowner");
    expect(firstTitle).toHaveValue("Document kitchen requirements with the homeowner");
  });

  it("confirms the edited draft and reports the created project", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.confirmProject.mockResolvedValue({
      runId: "run-1",
      projectId: 42,
      projectName: "Kitchen Redesign Project",
      taskIds: [101, 102, 103],
      alreadyConfirmed: false,
    });
    render(<App client={client} />);
    await generateDraft(user, client);

    const firstTitle = screen.getByLabelText(/title for ticket 1/i);
    await user.clear(firstTitle);
    await user.type(firstTitle, "Document kitchen requirements with the homeowner");
    const confirmButton = screen.getByRole("button", { name: /confirm and create project/i });
    const invalidFieldIds = Array.from(confirmButton.closest("form").elements)
      .filter((field) => !field.checkValidity())
      .map((field) => field.id);
    expect(invalidFieldIds).toEqual([]);
    await user.click(confirmButton);

    expect(client.confirmProject).toHaveBeenCalledWith({
      token: "jwt-token",
      runId: "run-1",
      draft: expect.objectContaining({
        tickets: expect.arrayContaining([
          expect.objectContaining({
            client_id: "design-kitchen",
            title: "Document kitchen requirements with the homeowner",
          }),
        ]),
      }),
    });
    expect(await screen.findByRole("heading", { name: /project created/i })).toBeInTheDocument();
    expect(screen.getByText(/project #42/i)).toBeInTheDocument();
    expect(screen.getByText(/3 tickets/i)).toBeInTheDocument();
  });

  it("opens the projects section and loads one project's ticket details", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^projects$/i }));

    expect(await screen.findByRole("heading", { name: /your projects/i })).toBeInTheDocument();
    expect(client.getProjects).toHaveBeenCalledWith({ token: "jwt-token" });
    expect(screen.getAllByText(/6 tickets/i)).toHaveLength(2);

    await user.click(
      screen.getByRole("button", { name: /job application tracker - initial backlog/i }),
    );

    expect(client.getProjectTasks).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
    });
    expect(
      await screen.findByRole("heading", { name: "Create opportunity intake" }),
    ).toBeInTheDocument();
    expect(screen.getByText("4.5 hours")).toBeInTheDocument();
    expect(
      screen.getByText("A saved opportunity includes company, role, and source"),
    ).toBeInTheDocument();
    expect(screen.getByText("Aug 14, 2026")).toBeInTheDocument();
    expect(screen.getByText("opportunity-intake")).toBeInTheDocument();
  });

  it("creates a project manually without invoking AI", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^projects$/i }));
    await screen.findByRole("heading", { name: /your projects/i });
    await user.click(screen.getByRole("button", { name: /new project/i }));
    const projectName = screen.getByLabelText(/project name/i);
    expect(projectName).toHaveAttribute("maxLength", "150");
    await user.type(projectName, "Release checklist");
    await user.type(screen.getByLabelText(/^objective$/i), "Ship the next release with confidence");
    await user.click(screen.getByRole("button", { name: /^create project$/i }));

    expect(client.createProject).toHaveBeenCalledWith({
      token: "jwt-token",
      project: {
        name: "Release checklist",
        objective: "Ship the next release with confidence",
      },
    });
    expect(client.generateProject).not.toHaveBeenCalled();
    expect(await screen.findByRole("heading", { name: "Release checklist" }))
      .toBeInTheDocument();
  });

  it("announces the selected navigation and project loading state", async () => {
    const user = userEvent.setup();
    const client = createClient();
    let resolveProjects;
    client.getProjects.mockReturnValue(
      new Promise((resolve) => {
        resolveProjects = resolve;
      }),
    );
    render(<App client={client} />);
    await logIn(user, client);

    const projectsButton = screen.getByRole("button", { name: /^projects$/i });
    await user.click(projectsButton);

    expect(projectsButton).toHaveAttribute("aria-current", "page");
    const projectsSection = screen.getByRole("heading", { name: /your projects/i })
      .closest("section");
    expect(projectsSection).toHaveAttribute("aria-busy", "true");

    await act(async () => {
      resolveProjects(savedProjects);
    });

    expect(projectsSection).toHaveAttribute("aria-busy", "false");
  });

  it("shows an empty project index", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.getProjects.mockResolvedValue([]);
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^projects$/i }));

    expect(await screen.findByText(/no projects yet/i)).toBeInTheDocument();
  });

  it("keeps project navigation available when the index request fails", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.getProjects.mockRejectedValue(
      new ApiError("Could not load projects", { status: 500 }),
    );
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^projects$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load projects");
    expect(screen.getByRole("button", { name: /^workshop$/i })).toBeInTheDocument();
  });

  it("does not describe a failed backlog request as an empty project", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.getProjectTasks.mockRejectedValue(
      new ApiError("Could not load the project backlog", { status: 500 }),
    );
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^projects$/i }));
    await user.click(
      await screen.findByRole("button", {
        name: /job application tracker - initial backlog/i,
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the project backlog",
    );
    expect(screen.queryByText(/no tickets in this project/i)).not.toBeInTheDocument();
  });

  it("opens a newly confirmed project from the creation receipt", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.confirmProject.mockResolvedValue({
      runId: "run-1",
      projectId: 42,
      projectName: "Kitchen Redesign Project",
      taskIds: [101, 102, 103],
      alreadyConfirmed: false,
    });
    client.getProjects.mockResolvedValue([
      {
        ...savedProjects[0],
        id: 42,
        name: "Kitchen Redesign Project",
        taskCount: 3,
      },
    ]);
    render(<App client={client} />);
    await generateDraft(user, client);
    await user.click(screen.getByRole("button", { name: /confirm and create project/i }));

    await user.click(await screen.findByRole("button", { name: /view project/i }));

    expect(client.getProjects).toHaveBeenCalledWith({ token: "jwt-token" });
    expect(client.getProjectTasks).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 42,
    });
    expect(await screen.findByRole("heading", { name: "Create opportunity intake" }))
      .toBeInTheDocument();
  });

  it("opens a project board and saves ticket edits from the detail panel", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^board$/i }));

    expect(await screen.findByRole("heading", { name: /project board/i }))
      .toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^todo$/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /in progress/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^blocked$/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /^done$/i })).toBeInTheDocument();
    expect(client.getProjects).toHaveBeenCalledWith({ token: "jwt-token" });
    expect(client.getProjectTasks).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
    });

    await user.click(
      screen.getByRole("button", { name: /open create opportunity intake/i }),
    );

    expect(screen.getByRole("dialog", { name: /edit create opportunity intake/i }))
      .toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: /edit create opportunity intake/i }))
      .not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /open create opportunity intake/i }),
    );
    const panel = screen.getByRole("dialog", { name: /edit create opportunity intake/i });
    const title = within(panel).getByLabelText(/^title$/i);
    await user.clear(title);
    await user.type(title, "Capture qualified opportunity details");
    await user.selectOptions(within(panel).getByLabelText(/^status$/i), "IN_PROGRESS");
    await user.clear(within(panel).getByLabelText(/due date/i));
    await user.click(within(panel).getByRole("button", { name: /save ticket/i }));

    expect(client.updateTask).toHaveBeenCalledWith({
      token: "jwt-token",
      taskId: 201,
      task: expect.objectContaining({
        title: "Capture qualified opportunity details",
        status: "IN_PROGRESS",
        projectId: 20,
        dueDate: null,
      }),
    });
    expect(await screen.findByText("Capture qualified opportunity details"))
      .toBeInTheDocument();
  });

  it("plans one existing ticket with project context and returns to the same board", async () => {
    const user = userEvent.setup();
    const client = createClient();
    client.confirmProject.mockResolvedValue({
      runId: "run-1",
      projectId: 20,
      projectName: "Job Application Tracker - Initial Backlog",
      taskIds: [301, 302, 303],
      alreadyConfirmed: false,
    });
    render(<App client={client} />);
    await logIn(user, client);
    await user.click(screen.getByRole("button", { name: /^board$/i }));
    await screen.findByRole("heading", { name: /project board/i });

    await user.click(
      screen.getByRole("button", { name: /open create opportunity intake/i }),
    );
    const panel = screen.getByRole("dialog", { name: /edit create opportunity intake/i });
    await user.click(within(panel).getByRole("button", { name: /plan with ai/i }));

    expect(screen.getByRole("button", { name: /^workshop$/i })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("heading", { name: /plan this ticket/i })).toBeInTheDocument();
    expect(screen.getByText("Job Application Tracker - Initial Backlog")).toBeInTheDocument();
    expect(screen.getByText("Create opportunity intake")).toBeInTheDocument();
    expect(screen.getByText(/nothing changes until confirmation/i)).toBeInTheDocument();

    const instructions = screen.getByLabelText(/planning instructions/i);
    expect(instructions.value).toContain("actionable implementation plan");
    await user.click(screen.getByRole("button", { name: /generate task plan/i }));

    expect(client.generateTaskPlan).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
      taskId: 201,
      prompt: instructions.value,
    });
    expect(await screen.findByLabelText(/refined ticket title/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /confirm and add tickets/i }));

    expect(client.confirmProject).toHaveBeenCalledWith({
      token: "jwt-token",
      runId: "run-1",
      draft: generatedDraft.draft,
    });
    expect(await screen.findByRole("heading", { name: /ticket plan added/i }))
      .toBeInTheDocument();
    expect(screen.getByText(/3 child tickets/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /open project board/i }));

    expect(client.getProjectTasks).toHaveBeenLastCalledWith({
      token: "jwt-token",
      projectId: 20,
    });
    expect(await screen.findByRole("heading", { name: /project board/i }))
      .toBeInTheDocument();
  });

  it("creates a manual ticket and deletes it only after confirmation", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);
    await user.click(screen.getByRole("button", { name: /^board$/i }));
    await screen.findByRole("heading", { name: /project board/i });

    await user.click(screen.getByRole("button", { name: /add ticket/i }));
    await user.type(screen.getByLabelText(/ticket title/i), "Prepare release notes");
    await user.type(
      screen.getByLabelText(/ticket description/i),
      "Summarize changes and operator actions for the release.",
    );
    await user.selectOptions(screen.getByLabelText(/ticket priority/i), "HIGH");
    await user.type(screen.getByLabelText(/ticket category/i), "Release");
    await user.type(screen.getByLabelText(/ticket due date/i), "2026-08-22");
    await user.click(screen.getByRole("button", { name: /^create ticket$/i }));

    expect(client.createTask).toHaveBeenCalledWith({
      token: "jwt-token",
      task: {
        title: "Prepare release notes",
        description: "Summarize changes and operator actions for the release.",
        status: "TODO",
        projectId: 20,
        assigneeId: null,
        priority: "HIGH",
        category: "Release",
        dueDate: "2026-08-22",
        position: null,
      },
    });
    expect(await screen.findByText("Prepare release notes")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /open prepare release notes/i }));
    const panel = screen.getByRole("dialog", { name: /edit prepare release notes/i });
    await user.click(within(panel).getByRole("button", { name: /^delete ticket$/i }));
    expect(within(panel).getByText(/permanently removes this ticket/i)).toBeInTheDocument();
    expect(client.deleteTask).not.toHaveBeenCalled();
    await user.click(within(panel).getByRole("button", { name: /yes, delete ticket/i }));

    expect(client.deleteTask).toHaveBeenCalledWith({ token: "jwt-token", taskId: 203 });
    expect(screen.queryByText("Prepare release notes")).not.toBeInTheDocument();
  });

  it("loads only the authenticated user's assigned queue into My Work", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /my work/i }));

    expect(await screen.findByRole("heading", { name: /my work/i })).toBeInTheDocument();
    expect(client.getMyWork).toHaveBeenCalledWith({ token: "jwt-token" });
    expect(client.getProjects).not.toHaveBeenCalled();
    expect(client.getProjectTasks).not.toHaveBeenCalled();
    expect(await screen.findByText("Repair the reservation handoff")).toBeInTheDocument();
    expect(screen.getByText("Catalog the remaining hand tools")).toBeInTheDocument();
    expect(screen.queryByText("Create opportunity intake")).not.toBeInTheDocument();
    const overdueTicket = screen.getByRole("button", {
      name: /open reconcile the inventory audit/i,
    });
    expect(within(overdueTicket).getByText(/^overdue$/i)).toBeInTheDocument();
    await user.click(overdueTicket);
    const personalTicket = screen.getByRole("dialog", { name: /edit reconcile the inventory audit/i });
    expect(within(personalTicket).queryByLabelText(/^assignee$/i)).not.toBeInTheDocument();
    expect(within(personalTicket).queryByLabelText(/^priority$/i)).not.toBeInTheDocument();
    expect(within(personalTicket).queryByRole("button", { name: /delete ticket/i }))
      .not.toBeInTheDocument();
    expect(within(personalTicket).queryByRole("button", { name: /plan with ai/i }))
      .not.toBeInTheDocument();
    await user.click(within(personalTicket).getByRole("button", { name: /close ticket/i }));
    expect(screen.getByRole("heading", { name: /blocked/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /due next/i })).toBeInTheDocument();
    expect(screen.getAllByText("Job Application Tracker - Initial Backlog").length)
      .toBeGreaterThan(0);
    expect(screen.getAllByText("Neighborhood Tool Lending Library - Phase 1").length)
      .toBeGreaterThan(0);
  });

  it("manages project participants and assigns a ticket to a member", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);
    await user.click(screen.getByRole("button", { name: /^board$/i }));
    await screen.findByRole("heading", { name: /project board/i });

    expect(client.getProjectMembers).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
    });
    await user.click(screen.getByRole("button", { name: /^people$/i }));
    const peoplePanel = screen.getByLabelText(/project participants/i);
    expect(within(peoplePanel).getByText("Pablo Local Tester")).toBeInTheDocument();
    expect(within(peoplePanel).getByText("Bob Builder")).toBeInTheDocument();
    await user.type(screen.getByLabelText(/participant username/i), "carol");
    await user.click(screen.getByRole("button", { name: /add participant/i }));

    expect(client.addProjectMember).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
      username: "carol",
    });
    expect(await screen.findByText("Carol Coordinator")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /open create opportunity intake/i }));
    const ticketPanel = screen.getByRole("dialog", { name: /edit create opportunity intake/i });
    await user.selectOptions(within(ticketPanel).getByLabelText(/^assignee$/i), "2");
    await user.click(within(ticketPanel).getByRole("button", { name: /save ticket/i }));
    expect(client.updateTask).toHaveBeenCalledWith({
      token: "jwt-token",
      taskId: 201,
      task: expect.objectContaining({ assigneeId: 2 }),
    });

    await user.click(screen.getByRole("button", { name: /remove bob builder/i }));
    expect(screen.getByText(/unassigns their tickets from this project/i)).toBeInTheDocument();
    expect(client.removeProjectMember).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: /^remove participant$/i }));

    expect(client.removeProjectMember).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
      userId: 2,
    });
    expect(within(peoplePanel).queryByText("Bob Builder")).not.toBeInTheDocument();
  });

  it("opens an honest AI follow-up brief from the project desk", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);
    await user.click(screen.getByRole("button", { name: /^board$/i }));
    await screen.findByRole("heading", { name: /project board/i });

    await user.click(screen.getByRole("button", { name: /project settings/i }));
    expect(screen.getByLabelText(/project settings form/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /plan next phase/i }));

    expect(screen.getByText(/does not modify this project/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /open follow-up brief/i }));

    expect(screen.getByRole("button", { name: /^workshop$/i })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByLabelText(/describe your project/i).value).toContain(
      "Job Application Tracker - Initial Backlog",
    );
  });

  it("edits and explicitly confirms deletion of an owned project", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);
    await user.click(screen.getByRole("button", { name: /^board$/i }));
    await screen.findByRole("heading", { name: /project board/i });

    await user.click(screen.getByRole("button", { name: /project settings/i }));
    const name = screen.getByLabelText(/project name/i);
    expect(name).toHaveAttribute("maxLength", "150");
    await user.clear(name);
    await user.type(name, "Job search command center");
    const objective = screen.getByLabelText(/^objective$/i);
    await user.clear(objective);
    await user.type(objective, "Track every application and next action");
    await user.click(screen.getByRole("button", { name: /save project/i }));

    expect(client.updateProject).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
      project: {
        name: "Job search command center",
        objective: "Track every application and next action",
      },
    });
    expect((await screen.findAllByText("Job search command center")).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: /^delete project$/i }));
    expect(screen.getByText(/deletes every ticket in this project/i)).toBeInTheDocument();
    expect(client.deleteProject).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: /yes, delete project/i }));

    expect(client.deleteProject).toHaveBeenCalledWith({
      token: "jwt-token",
      projectId: 20,
    });
    expect(screen.queryByText("Job search command center")).not.toBeInTheDocument();
  });

  it("shows the authenticated account and signs out from its dedicated view", async () => {
    const user = userEvent.setup();
    const client = createClient();
    render(<App client={client} />);
    await logIn(user, client);

    await user.click(screen.getByRole("button", { name: /^account$/i }));

    const accountTitle = await screen.findByRole("heading", { name: /account/i });
    const account = accountTitle.closest("section");
    expect(within(account).getByText("Pablo Local Tester")).toBeInTheDocument();
    expect(within(account).getByText("pablo-local")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /sign out of workspace/i }));

    expect(await screen.findByRole("heading", { name: /enter the project workshop/i }))
      .toBeInTheDocument();
  });
});
