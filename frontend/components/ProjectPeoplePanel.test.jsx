import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import ProjectPeoplePanel from "./ProjectPeoplePanel";

const project = { id: 20, name: "Release desk" };
const members = [
  { userId: 1, username: "alice", fullName: "Alice Owner", role: "OWNER" },
  { userId: 2, username: "morgan", fullName: "Morgan Manager", role: "MANAGER" },
  { userId: 3, username: "mel", fullName: "Mel Member", role: "MEMBER" },
];
const invitations = [
  {
    invitationId: 71,
    projectId: 20,
    email: "pending.member@example.com",
    role: "MEMBER",
    state: "PENDING",
    expiresAt: "2026-08-19T12:00:00Z",
  },
  {
    invitationId: 72,
    projectId: 20,
    email: "pending.manager@example.com",
    role: "MANAGER",
    state: "PENDING",
    expiresAt: "2026-08-20T12:00:00Z",
  },
];

const renderPanel = (overrides = {}) => {
  const props = {
    project,
    members,
    invitations,
    actorRole: "OWNER",
    loadPhase: "idle",
    loadError: null,
    mutationPhase: "idle",
    mutationError: null,
    onInvite: vi.fn(),
    onRevokeInvitation: vi.fn(),
    onUpdateMemberRole: vi.fn(),
    onRemoveMember: vi.fn(),
    onRetry: vi.fn(),
    ...overrides,
  };
  render(<ProjectPeoplePanel {...props} />);
  return props;
};

describe("ProjectPeoplePanel", () => {
  it("invites by email and copies only the URL returned by creation", async () => {
    const user = userEvent.setup();
    const clipboard = { writeText: vi.fn().mockResolvedValue(undefined) };
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: clipboard });
    const privateInviteUrl = "https://app.test/invite#token=private-token";
    const onInvite = vi.fn().mockResolvedValue({
      ...invitations[0],
      invitationId: 73,
      email: "new.manager@example.com",
      role: "MANAGER",
      inviteUrl: privateInviteUrl,
    });
    renderPanel({ onInvite });

    expect(screen.queryByRole("button", { name: /copy invite link/i })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText(/invite email/i), "new.manager@example.com");
    await user.selectOptions(screen.getByLabelText(/invitation role/i), "MANAGER");
    await user.click(screen.getByRole("button", { name: /^send invitation$/i }));

    expect(onInvite).toHaveBeenCalledWith(20, {
      email: "new.manager@example.com",
      role: "MANAGER",
    });
    expect(document.body).not.toHaveTextContent(privateInviteUrl);
    expect(document.body).not.toHaveTextContent("private-token");
    await user.click(await screen.findByRole("button", { name: /copy invite link/i }));
    expect(clipboard.writeText).toHaveBeenCalledWith(privateInviteUrl);
    expect(await screen.findByRole("status")).toHaveTextContent("Invite link copied");
  });

  it("shows pending state and expiry and revokes an allowed invitation", async () => {
    const user = userEvent.setup();
    const onRevokeInvitation = vi.fn().mockResolvedValue(true);
    renderPanel({ onRevokeInvitation });

    const pendingMember = screen.getByText("pending.member@example.com").closest("li");
    expect(within(pendingMember).getByText(/^pending$/i)).toBeInTheDocument();
    expect(within(pendingMember).getByText(/expires aug 19, 2026/i)).toBeInTheDocument();
    await user.click(within(pendingMember).getByRole("button", {
      name: /revoke pending.member@example.com invitation/i,
    }));

    expect(onRevokeInvitation).toHaveBeenCalledWith(20, invitations[0]);
  });

  it("lets owners change member and manager roles but never the owner role", async () => {
    const user = userEvent.setup();
    const onUpdateMemberRole = vi.fn().mockResolvedValue(true);
    renderPanel({ onUpdateMemberRole });

    expect(screen.queryByLabelText(/change alice owner role/i)).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText(/change mel member role/i), "MANAGER");
    expect(onUpdateMemberRole).toHaveBeenCalledWith(20, members[2], "MANAGER");
    await user.selectOptions(screen.getByLabelText(/change morgan manager role/i), "MEMBER");
    expect(onUpdateMemberRole).toHaveBeenCalledWith(20, members[1], "MEMBER");
  });

  it("limits managers to inviting, revoking, and removing members", () => {
    renderPanel({ actorRole: "MANAGER" });

    expect(screen.getByLabelText(/invitation role/i)).toHaveValue("MEMBER");
    expect(screen.getByLabelText(/invitation role/i)).toBeDisabled();
    expect(screen.getByRole("button", { name: /revoke pending.member@example.com invitation/i }))
      .toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /revoke pending.manager@example.com invitation/i }))
      .not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /remove mel member/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /remove morgan manager/i }))
      .not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /change .* role/i })).not.toBeInTheDocument();
  });

  it.each(["MEMBER", "UNKNOWN", undefined])(
    "keeps the %s role view-only and fails closed",
    (actorRole) => {
      renderPanel({ actorRole });

      expect(screen.queryByLabelText(/invite email/i)).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /revoke .* invitation/i }))
        .not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /remove /i })).not.toBeInTheDocument();
      expect(screen.queryByRole("combobox", { name: /change .* role/i }))
        .not.toBeInTheDocument();
    },
  );
});
