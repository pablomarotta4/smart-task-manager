import { act } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import { ApiError } from "./api";

const authenticatedUser = {
  token: "jwt-token",
  user: {
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

const createClient = () => ({
  login: vi.fn().mockResolvedValue(authenticatedUser),
  generateProject: vi.fn().mockResolvedValue(generatedDraft),
  confirmProject: vi.fn(),
  getProjects: vi.fn().mockResolvedValue(savedProjects),
  getProjectTasks: vi.fn().mockResolvedValue(savedProjectTasks),
  updateTask: vi.fn().mockImplementation(({ taskId, task }) =>
    Promise.resolve({ id: taskId, ...task })),
  updateTaskStatus: vi.fn().mockImplementation(({ taskId, status }) =>
    Promise.resolve({ id: taskId, status })),
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

    const panel = screen.getByRole("dialog", { name: /edit create opportunity intake/i });
    const title = within(panel).getByLabelText(/^title$/i);
    await user.clear(title);
    await user.type(title, "Capture qualified opportunity details");
    await user.selectOptions(within(panel).getByLabelText(/^status$/i), "IN_PROGRESS");
    await user.click(within(panel).getByRole("button", { name: /save ticket/i }));

    expect(client.updateTask).toHaveBeenCalledWith({
      token: "jwt-token",
      taskId: 201,
      task: expect.objectContaining({
        title: "Capture qualified opportunity details",
        status: "IN_PROGRESS",
        projectId: 20,
      }),
    });
    expect(await screen.findByText("Capture qualified opportunity details"))
      .toBeInTheDocument();
  });
});
