import { describe, expect, it } from "vitest";

import {
  ASSIGNED_WORK_PERMISSIONS,
  NO_PROJECT_PERMISSIONS,
  assignedWorkPermissions,
  canChangeProjectMemberRole,
  canInviteProjectRole,
  canRemoveProjectMember,
  canRevokeProjectInvitation,
  projectPermissions,
  ticketPermissions,
} from "./projectPermissions";

describe("project permissions", () => {
  it.each([
    ["OWNER", {
      canViewProject: true,
      canViewMembers: true,
      canEditProject: true,
      canDeleteProject: true,
      canManageMembers: true,
      canCreateTask: true,
      canEditAnyTask: true,
      canChangePriority: true,
      canChangeDueDate: true,
      canChangeCategory: true,
      canAssignTask: true,
      canChangePosition: true,
      canDeleteTask: true,
      canPlanTask: true,
      canPlanProject: true,
    }],
    ["MANAGER", {
      canViewProject: true,
      canViewMembers: true,
      canEditProject: false,
      canDeleteProject: false,
      canManageMembers: true,
      canCreateTask: true,
      canEditAnyTask: true,
      canChangePriority: true,
      canChangeDueDate: true,
      canChangeCategory: true,
      canAssignTask: true,
      canChangePosition: true,
      canDeleteTask: true,
      canPlanTask: true,
      canPlanProject: true,
    }],
    ["MEMBER", {
      canViewProject: true,
      canViewMembers: true,
      canEditProject: false,
      canDeleteProject: false,
      canManageMembers: false,
      canCreateTask: false,
      canEditAnyTask: false,
      canChangePriority: false,
      canChangeDueDate: false,
      canChangeCategory: false,
      canAssignTask: false,
      canChangePosition: false,
      canDeleteTask: false,
      canPlanTask: false,
      canPlanProject: false,
    }],
  ])("derives the %s capability matrix", (role, expected) => {
    expect(projectPermissions(role)).toEqual(expect.objectContaining(expected));
  });

  it.each([undefined, null, "", "owner", "UNKNOWN", "__proto__", "toString"])(
    "fails closed for the role %s",
    (role) => {
      expect(projectPermissions(role)).toBe(NO_PROJECT_PERMISSIONS);
    },
  );

  it("lets a member edit only a ticket assigned to the current account", () => {
    expect(ticketPermissions({
      projectRole: "MEMBER",
      task: { assigneeId: 18 },
      currentUserId: 18,
    })).toEqual(expect.objectContaining({
      canEditTask: true,
      canChangeStatus: true,
      canChangePriority: false,
      canChangeDueDate: false,
      canChangeCategory: false,
      canAssignTask: false,
      canChangePosition: false,
      canDeleteTask: false,
      canPlanTask: false,
    }));

    expect(ticketPermissions({
      projectRole: "MEMBER",
      task: { assigneeId: 22 },
      currentUserId: 18,
    })).toEqual(expect.objectContaining({
      canEditTask: false,
      canChangeStatus: false,
    }));
  });

  it("gives owners and managers full ticket controls", () => {
    for (const projectRole of ["OWNER", "MANAGER"]) {
      expect(ticketPermissions({
        projectRole,
        task: { assigneeId: null },
        currentUserId: 18,
      })).toEqual(expect.objectContaining({
        canEditTask: true,
        canChangeStatus: true,
        canChangePriority: true,
        canChangeDueDate: true,
        canChangeCategory: true,
        canAssignTask: true,
        canChangePosition: true,
        canDeleteTask: true,
        canPlanTask: true,
      }));
    }
  });

  it("derives invitation and member-role controls without trusting unknown roles", () => {
    expect(canInviteProjectRole({ actorRole: "OWNER", invitationRole: "MANAGER" })).toBe(true);
    expect(canInviteProjectRole({ actorRole: "OWNER", invitationRole: "MEMBER" })).toBe(true);
    expect(canInviteProjectRole({ actorRole: "MANAGER", invitationRole: "MEMBER" })).toBe(true);
    expect(canInviteProjectRole({ actorRole: "MANAGER", invitationRole: "MANAGER" })).toBe(false);
    expect(canInviteProjectRole({ actorRole: "UNKNOWN", invitationRole: "MEMBER" })).toBe(false);

    expect(canRevokeProjectInvitation({ actorRole: "MANAGER", invitationRole: "MEMBER" }))
      .toBe(true);
    expect(canRevokeProjectInvitation({ actorRole: "MANAGER", invitationRole: "MANAGER" }))
      .toBe(false);
    expect(canChangeProjectMemberRole({
      actorRole: "OWNER",
      targetRole: "MEMBER",
      nextRole: "MANAGER",
    })).toBe(true);
    expect(canChangeProjectMemberRole({
      actorRole: "MANAGER",
      targetRole: "MEMBER",
      nextRole: "MANAGER",
    })).toBe(false);
    expect(canChangeProjectMemberRole({
      actorRole: "OWNER",
      targetRole: "OWNER",
      nextRole: "MEMBER",
    })).toBe(false);
  });

  it("keeps assigned work explicitly editable without granting management", () => {
    expect(ASSIGNED_WORK_PERMISSIONS).toEqual(expect.objectContaining({
      canEditTask: true,
      canChangeStatus: true,
      canChangePriority: false,
      canChangeDueDate: false,
      canChangeCategory: false,
      canAssignTask: false,
      canChangePosition: false,
      canDeleteTask: false,
      canPlanTask: false,
    }));
  });

  it("fails closed for My Work items that are not assigned to the current account", () => {
    expect(assignedWorkPermissions({
      task: { assigneeId: 18 },
      currentUserId: 18,
    })).toBe(ASSIGNED_WORK_PERMISSIONS);
    expect(assignedWorkPermissions({
      task: { assigneeId: 22 },
      currentUserId: 18,
    })).toEqual(expect.objectContaining({
      canEditTask: false,
      canChangeStatus: false,
      canChangeDueDate: false,
      canChangeCategory: false,
    }));
    expect(assignedWorkPermissions({
      task: { assigneeId: null },
      currentUserId: 18,
    })).toEqual(expect.objectContaining({ canEditTask: false }));
  });

  it.each([
    ["OWNER", "MEMBER", true],
    ["OWNER", "MANAGER", true],
    ["OWNER", "OWNER", false],
    ["MANAGER", "MEMBER", true],
    ["MANAGER", "MANAGER", false],
    ["MANAGER", "OWNER", false],
    ["MEMBER", "MEMBER", false],
    [null, "MEMBER", false],
  ])("checks whether %s can remove %s", (actorRole, targetRole, expected) => {
    expect(canRemoveProjectMember({ actorRole, targetRole })).toBe(expected);
  });
});
