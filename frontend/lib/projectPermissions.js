const createProjectCapabilitySet = (overrides = {}) => Object.freeze({
  role: null,
  canViewProject: false,
  canViewMembers: false,
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
  ...overrides,
});

export const NO_PROJECT_PERMISSIONS = createProjectCapabilitySet();

const PROJECT_PERMISSIONS_BY_ROLE = Object.freeze({
  OWNER: createProjectCapabilitySet({
    role: "OWNER",
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
  }),
  MANAGER: createProjectCapabilitySet({
    role: "MANAGER",
    canViewProject: true,
    canViewMembers: true,
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
  }),
  MEMBER: createProjectCapabilitySet({
    role: "MEMBER",
    canViewProject: true,
    canViewMembers: true,
  }),
});

const createTicketCapabilitySet = (overrides = {}) => Object.freeze({
  canEditTask: false,
  canChangeStatus: false,
  canChangePriority: false,
  canChangeDueDate: false,
  canChangeCategory: false,
  canAssignTask: false,
  canChangePosition: false,
  canDeleteTask: false,
  canPlanTask: false,
  ...overrides,
});

const NO_TICKET_PERMISSIONS = createTicketCapabilitySet();
const MANAGED_TICKET_PERMISSIONS = createTicketCapabilitySet({
  canEditTask: true,
  canChangeStatus: true,
  canChangePriority: true,
  canChangeDueDate: true,
  canChangeCategory: true,
  canAssignTask: true,
  canChangePosition: true,
  canDeleteTask: true,
  canPlanTask: true,
});

export const ASSIGNED_WORK_PERMISSIONS = createTicketCapabilitySet({
  canEditTask: true,
  canChangeStatus: true,
});

export const assignedWorkPermissions = ({ task, currentUserId }) => {
  const assignedToCurrentUser = task?.assigneeId != null
    && currentUserId != null
    && String(task.assigneeId) === String(currentUserId);
  return assignedToCurrentUser ? ASSIGNED_WORK_PERMISSIONS : NO_TICKET_PERMISSIONS;
};

export const projectPermissions = (role) => (
  Object.hasOwn(PROJECT_PERMISSIONS_BY_ROLE, role)
    ? PROJECT_PERMISSIONS_BY_ROLE[role]
    : NO_PROJECT_PERMISSIONS
);

export const ticketPermissions = ({ projectRole, task, currentUserId }) => {
  const permissions = projectPermissions(projectRole);
  if (permissions.canEditAnyTask) return MANAGED_TICKET_PERMISSIONS;

  const assignedToCurrentUser = task?.assigneeId != null
    && currentUserId != null
    && String(task.assigneeId) === String(currentUserId);
  return permissions.role === "MEMBER" && assignedToCurrentUser
    ? ASSIGNED_WORK_PERMISSIONS
    : NO_TICKET_PERMISSIONS;
};

export const canRemoveProjectMember = ({ actorRole, targetRole }) => {
  if (targetRole === "OWNER") return false;
  if (actorRole === "OWNER") return targetRole === "MANAGER" || targetRole === "MEMBER";
  return actorRole === "MANAGER" && targetRole === "MEMBER";
};
