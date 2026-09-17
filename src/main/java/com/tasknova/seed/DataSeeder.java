package com.tasknova.seed;

import com.tasknova.entity.Tag;
import com.tasknova.entity.Task;
import com.tasknova.entity.User;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.Role;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.repository.TagRepository;
import com.tasknova.repository.TaskRepository;
import com.tasknova.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Seeds the database with initial demo data on first startup.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Each seed block is <strong>idempotent</strong> — it checks for
 *       existence before inserting, so re-running the app never duplicates data.</li>
 *   <li>Seeding is skipped entirely once real users/tasks are present.</li>
 *   <li>No domain/service class is modified — all data goes through
 *       repositories directly to avoid triggering AOP audit events during boot.</li>
 *   <li>Active in all profiles (including prod) to ensure the admin account
 *       is always available on a fresh deployment.</li>
 * </ul>
 *
 * <p><strong>Seeded credentials:</strong>
 * <ul>
 *   <li>Admin  : {@code admin@tasknova.com} / {@code Admin@1234}</li>
 *   <li>Demo   : {@code demo@tasknova.com}  / {@code Demo@1234}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository    userRepository;
    private final TaskRepository    taskRepository;
    private final TagRepository     tagRepository;
    private final PasswordEncoder   passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataSeeder — checking seed requirements...");

        User admin    = seedAdmin();
        User demoUser = seedDemoUser();
        seedTags();
        seedSampleTasks(demoUser);

        log.info("DataSeeder — completed.");
    }

    // ────────────────────────────────────────────────────────────────
    //  Admin Account
    // ────────────────────────────────────────────────────────────────

    private User seedAdmin() {
        String adminEmail = "admin@tasknova.com";
        return userRepository.findByEmail(adminEmail).orElseGet(() -> {
            User admin = User.builder()
                    .username("admin")
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin@1234"))
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            User saved = userRepository.save(admin);
            log.info("✓ Admin account seeded: {}", adminEmail);
            return saved;
        });
    }

    // ────────────────────────────────────────────────────────────────
    //  Demo User Account
    // ────────────────────────────────────────────────────────────────

    private User seedDemoUser() {
        String demoEmail = "demo@tasknova.com";
        return userRepository.findByEmail(demoEmail).orElseGet(() -> {
            User demo = User.builder()
                    .username("demo_user")
                    .email(demoEmail)
                    .password(passwordEncoder.encode("Demo@1234"))
                    .role(Role.USER)
                    .active(true)
                    .build();
            User saved = userRepository.save(demo);
            log.info("✓ Demo user seeded: {}", demoEmail);
            return saved;
        });
    }

    // ────────────────────────────────────────────────────────────────
    //  Tags
    // ────────────────────────────────────────────────────────────────

    private void seedTags() {
        List<Object[]> tagDefs = List.of(
            new Object[]{"bug",       "#ef4444"},
            new Object[]{"feature",   "#4f46e5"},
            new Object[]{"urgent",    "#f97316"},
            new Object[]{"backend",   "#0ea5e9"},
            new Object[]{"frontend",  "#8b5cf6"},
            new Object[]{"review",    "#10b981"},
            new Object[]{"docs",      "#6b7280"},
            new Object[]{"testing",   "#f59e0b"}
        );

        for (Object[] def : tagDefs) {
            String name  = (String) def[0];
            String color = (String) def[1];
            if (!tagRepository.existsByNameIgnoreCase(name)) {
                tagRepository.save(Tag.builder().name(name).color(color).build());
                log.info("✓ Tag seeded: #{}", name);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Sample Tasks for Demo User
    // ────────────────────────────────────────────────────────────────

    private void seedSampleTasks(User demoUser) {
        // Only seed if this user has no tasks yet
        if (taskRepository.countByOwnerId(demoUser.getId()) > 0) {
            log.info("Sample tasks already present — skipping task seed.");
            return;
        }

        Tag featureTag = tagRepository.findByNameIgnoreCase("feature").orElse(null);
        Tag backendTag = tagRepository.findByNameIgnoreCase("backend").orElse(null);
        Tag urgentTag  = tagRepository.findByNameIgnoreCase("urgent").orElse(null);
        Tag bugTag     = tagRepository.findByNameIgnoreCase("bug").orElse(null);
        Tag docsTag    = tagRepository.findByNameIgnoreCase("docs").orElse(null);

        List<Task> sampleTasks = List.of(
            Task.builder()
                .title("Set up project structure")
                .description("Initialize the Spring Boot project, configure Maven dependencies, and set up the folder structure following domain-driven design principles.")
                .dueDate(LocalDate.now().minusDays(2))
                .priority(Priority.HIGH)
                .status(TaskStatus.DONE)
                .owner(demoUser)
                .tags(featureTag != null ? Set.of(featureTag) : Set.of())
                .build(),

            Task.builder()
                .title("Implement user authentication")
                .description("Add Spring Security with JWT-based authentication. Cover login, registration, and role-based access control for USER and ADMIN roles.")
                .dueDate(LocalDate.now().plusDays(3))
                .priority(Priority.HIGH)
                .status(TaskStatus.IN_PROGRESS)
                .owner(demoUser)
                .tags(backendTag != null && featureTag != null
                        ? Set.of(backendTag, featureTag) : Set.of())
                .build(),

            Task.builder()
                .title("Design the task dashboard UI")
                .description("Create a modern Thymeleaf dashboard with a dotted white background, stat cards, and a recent tasks widget. Use the Inter font and indigo accent colors.")
                .dueDate(LocalDate.now().plusDays(5))
                .priority(Priority.MEDIUM)
                .status(TaskStatus.TODO)
                .owner(demoUser)
                .tags(Set.of())
                .build(),

            Task.builder()
                .title("Fix pagination bug on task list")
                .description("The task list does not preserve filter parameters across page changes. Fix URL query param handling in the Thymeleaf template.")
                .dueDate(LocalDate.now().plusDays(1))
                .priority(Priority.HIGH)
                .status(TaskStatus.TODO)
                .owner(demoUser)
                .tags(bugTag != null && urgentTag != null
                        ? Set.of(bugTag, urgentTag) : Set.of())
                .build(),

            Task.builder()
                .title("Write API documentation")
                .description("Document all REST API endpoints using Springdoc OpenAPI. Include request/response schemas, authentication requirements, and example payloads.")
                .dueDate(LocalDate.now().plusDays(10))
                .priority(Priority.LOW)
                .status(TaskStatus.TODO)
                .owner(demoUser)
                .tags(docsTag != null ? Set.of(docsTag) : Set.of())
                .build()
        );

        taskRepository.saveAll(sampleTasks);
        log.info("✓ {} sample tasks seeded for demo user.", sampleTasks.size());
    }
}
