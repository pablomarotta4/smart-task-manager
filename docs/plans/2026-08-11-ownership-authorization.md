# Ownership Authorization Implementation Plan

> **For Codex:** Execute this plan task-by-task. If an `executing-plans` skill is installed, use it; otherwise follow the steps manually.

**Goal:** Ensure authenticated users can only create, read, update, assign, or delete their own projects and tickets, while administrators alone can enumerate all accounts.

**Architecture:** Keep the existing layered Spring architecture. Controllers derive the username from `Principal`; services enforce ownership through scoped repository lookups and return not-found for inaccessible resources to avoid leaking their existence. The client-supplied project username remains temporarily accepted for wire compatibility but is ignored.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA, JUnit 5, Mockito, MockMvc.

**Files to Understand:**

- `src/main/java/com/pablomarotta/smart_task_manager/config/SecurityConfig.java` - endpoint authentication rules.
- `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectController.java` - project HTTP boundary.
- `src/main/java/com/pablomarotta/smart_task_manager/controller/TaskController.java` - task HTTP boundary.
- `src/main/java/com/pablomarotta/smart_task_manager/controller/UserController.java` - account administration boundary.
- `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java` - project use cases.
- `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java` - task use cases.
- `src/main/java/com/pablomarotta/smart_task_manager/repository/ProjectRepository.java` - project ownership queries.
- `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java` - task ownership queries.
- `src/main/java/com/pablomarotta/smart_task_manager/security/UserDetailsServiceImpl.java` - role authorities.

---

### Task 1: Scope project operations to the principal

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/service/ProjectServiceTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/controller/ProjectControllerTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/ProjectRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectController.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/ProjectRequest.java`

**Steps:**

1. Add failing service tests proving `getAllProjects("alice")` uses only Alice's projects, inaccessible IDs return not-found, and `createProject(request, "alice")` ignores a forged request username.
2. Add failing controller tests proving every endpoint passes `principal.getName()`.
3. Run `mvn -Dtest=ProjectServiceTest,ProjectControllerTest test` with Java 21 and verify compilation/test failure for the missing scoped APIs.
4. Add repository methods:

```java
List<Project> findByOwnerUsernameOrderByCreatedAtDesc(String username);
Optional<Project> findByIdAndOwnerUsername(Long id, String username);
```

5. Change project service/controller signatures to accept the authenticated username, remove the validation requirement from `ProjectRequest.username`, and ignore that legacy field.
6. Run the focused tests and verify green.
7. Commit with `fix(security): scope project access to owners`.

### Task 2: Scope every task operation to an owned project

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/service/TaskServiceTest.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/controller/TaskControllerTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/TaskController.java`

**Steps:**

1. Add failing tests for foreign-project reads, foreign-task updates/deletes/status/priority/assignment changes, owner-scoped status lists, and owner-scoped creation.
2. Add controller tests proving the principal username reaches every task service call.
3. Run `mvn -Dtest=TaskServiceTest,TaskControllerTest test` and verify red.
4. Add owner-scoped repository methods:

```java
List<Task> findByProjectOwnerUsername(String username);
List<Task> findByStatusAndProjectOwnerUsername(Status status, String username);
List<Task> findByAssigneeIdAndProjectOwnerUsername(Long assigneeId, String username);
Optional<Task> findByIdAndProjectOwnerUsername(Long id, String username);
```

5. Require a username on every public task service method, use scoped lookups for reads and mutations, and require ownership before loading criteria or dependencies.
6. Return only the owner and actual assignees from the project-user endpoint; never enumerate unrelated accounts.
7. Run focused tests and verify green.
8. Commit with `fix(security): scope task access to project owners`.

### Task 3: Enforce account self-service and administrator roles

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/security/SecurityTest.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/security/UserDetailsServiceImplTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/security/UserDetailsServiceImpl.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/UserController.java`

**Steps:**

1. Add failing tests proving roles become `ROLE_USER` or `ROLE_ADMIN`, ordinary users cannot enumerate accounts, and users can only read/update/deactivate themselves.
2. Run focused security tests and verify red.
3. Map persisted roles into Spring authorities and enforce administrator enumeration plus principal-bound self-service.
4. Run focused tests and verify green.
5. Commit with `fix(security): enforce account access roles`.

### Task 4: Verify and merge the branch

**Files:**

- Modify only files required by review findings.

**Steps:**

1. Run all non-database Java tests with Java 21, `npm test`, `npm run build`, and `ai-service/.venv/bin/pytest ai-service/tests`.
2. Run `git diff --check` and review the entire `main..feature/ownership-authorization` diff for authorization bypasses and API regressions.
3. Fix all critical and important findings and repeat verification.
4. Commit the final branch state.
5. Fast-forward merge `feature/ownership-authorization` into `main` only after every gate passes.
