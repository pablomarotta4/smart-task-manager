package com.pablomarotta.smart_task_manager.model;

public enum ProjectRole {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean canManageProject() {
        return this == OWNER || this == MANAGER;
    }
}
