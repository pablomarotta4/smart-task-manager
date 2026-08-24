package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectAuthorizationContractTest {

    @Test
    void exposesRoleBasedProjectAuthorizationContracts() throws Exception {
        Class<?> projectRole = Class.forName("com.pablomarotta.smart_task_manager.model.ProjectRole");
        Class<?> accessPolicy = Class.forName("com.pablomarotta.smart_task_manager.service.ProjectAccessPolicy");

        assertNotNull(Enum.valueOf(projectRole.asSubclass(Enum.class), "OWNER"));
        assertNotNull(Enum.valueOf(projectRole.asSubclass(Enum.class), "MANAGER"));
        assertNotNull(Enum.valueOf(projectRole.asSubclass(Enum.class), "MEMBER"));
        assertDoesNotThrow(() -> accessPolicy.getDeclaredMethod("requireMember", Long.class, String.class));
        assertDoesNotThrow(() -> accessPolicy.getDeclaredMethod("requireManager", Long.class, String.class));
        assertDoesNotThrow(() -> accessPolicy.getDeclaredMethod("requireOwner", Long.class, String.class));
        assertDoesNotThrow(() -> accessPolicy.getDeclaredMethod("requireTaskViewer", Long.class, String.class));
        assertDoesNotThrow(() -> accessPolicy.getDeclaredMethod("requireTaskEditor", Long.class, String.class));
        assertDoesNotThrow(() -> ProjectResponse.class.getDeclaredMethod("getCurrentUserRole"));
        assertDoesNotThrow(() -> ProjectMemberResponse.class.getDeclaredMethod("getRole"));
    }
}
