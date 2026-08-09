import { act } from "react";
import { render, screen } from "@testing-library/react";
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

const createClient = () => ({
  login: vi.fn().mockResolvedValue(authenticatedUser),
  generateProject: vi.fn().mockResolvedValue(generatedDraft),
  confirmProject: vi.fn(),
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
});
