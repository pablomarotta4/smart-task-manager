# Copilot Instructions for Smart Task Manager

## Project Overview

A Spring Boot 4.0.1 REST API for task management with AI classification capabilities. Built with Java 21, PostgreSQL, and Maven.

## Tech Stack & Key Dependencies

- **Framework**: Spring Boot 4.0.1 (latest)
- **Java**: 21 (use modern features: records, pattern matching, virtual threads)
- **Database**: PostgreSQL with Spring Data JPA
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Build**: Maven with wrapper (`./mvnw`)
- **Code Generation**: Lombok (configured in annotation processors)

## Project Structure

```
src/main/java/com/pablomarotta/smart_task_manager/
├── SmartTaskManagerApplication.java  # Entry point
├── controller/                       # REST controllers (to be created)
├── service/                          # Business logic
├── repository/                       # JPA repositories
├── model/                            # JPA entities
└── dto/                              # Request/Response DTOs
```

## Developer Workflow

### Build & Run
```bash
./mvnw spring-boot:run           # Start with DevTools hot-reload
./mvnw clean package             # Build JAR
./mvnw test                      # Run tests
```

### Database
PostgreSQL required. Configure connection in `src/main/resources/application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_task_manager
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

## Coding Conventions

### Entity Pattern
Use Lombok annotations to reduce boilerplate:
```java
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    private String title;
    
    // Use Jakarta validation annotations
}
```

### REST Controller Pattern
```java
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    
    // Use constructor injection via Lombok
}
```

### Repository Pattern
```java
public interface TaskRepository extends JpaRepository<Task, Long> {
    // Use Spring Data derived query methods
}
```

## Testing

- Use `@SpringBootTest` for integration tests
- Use `@DataJpaTest` for repository tests (from `spring-boot-starter-data-jpa-test`)
- Use `@WebMvcTest` for controller tests (from `spring-boot-starter-webmvc-test`)

## AI Classification Feature

The project is intended to include AI-powered task classification. When implementing:
- Consider using a separate service for AI integration
- Keep AI logic decoupled from core task management

## Important Notes

- Package name uses underscores: `com.pablomarotta.smart_task_manager`
- DevTools enabled: changes auto-reload during development
- Lombok requires IDE plugin for full support
