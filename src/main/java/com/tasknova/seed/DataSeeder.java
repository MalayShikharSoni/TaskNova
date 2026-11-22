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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Seeds the database with initial demo data on first startup.
 *
 * <ul>
 *   <li>Each block is <strong>idempotent</strong>: it checks for existing rows first,
 *       so restarting never duplicates data.</li>
 *   <li>Data goes through repositories directly, so no audit events fire during boot.</li>
 *   <li>Runs in every profile so a fresh deployment always has an admin account.
 *       Set {@code ADMIN_PASSWORD} (and optionally {@code DEMO_PASSWORD}) on any
 *       deployment that isn't meant to be a public demo.</li>
 * </ul>
 */
@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@1234";
    public static final String DEFAULT_DEMO_PASSWORD  = "Demo@1234";

    private final UserRepository  userRepository;
    private final TaskRepository  taskRepository;
    private final TagRepository   tagRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String demoPassword;

    public DataSeeder(UserRepository userRepository,
                      TaskRepository taskRepository,
                      TagRepository tagRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.admin-password:" + DEFAULT_ADMIN_PASSWORD + "}") String adminPassword,
                      @Value("${app.seed.demo-password:" + DEFAULT_DEMO_PASSWORD + "}") String demoPassword) {
        this.userRepository  = userRepository;
        this.taskRepository  = taskRepository;
        this.tagRepository   = tagRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword   = adminPassword;
        this.demoPassword    = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataSeeder: checking seed requirements...");

        User admin    = seedUser("admin", "admin@tasknova.com", adminPassword, Role.ADMIN);
        User demoUser = seedUser("demo_user", "demo@tasknova.com", demoPassword, Role.USER);
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            log.warn("Admin account uses the public demo password. Set ADMIN_PASSWORD for real deployments.");
        }
        seedTags();
        seedSampleTasks(demoUser, admin);

        log.info("DataSeeder: completed.");
    }

    // ────────────────────────────────────────────────────────────────
    //  Accounts
    // ────────────────────────────────────────────────────────────────

    private User seedUser(String username, String email, String rawPassword, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User saved = userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .active(true)
                    .build());
            log.info("Seeded {} account: {}", role, email);
            return saved;
        });
    }

    // ────────────────────────────────────────────────────────────────
    //  Tags (colours match the UI palette)
    // ────────────────────────────────────────────────────────────────

    private void seedTags() {
        Map<String, String> tagDefs = new LinkedHashMap<>();
        tagDefs.put("bug",      "#e8384f");
        tagDefs.put("feature",  "#3a4fd8");
        tagDefs.put("urgent",   "#ff3fa4");
        tagDefs.put("backend",  "#0094b3");
        tagDefs.put("frontend", "#7b5cff");
        tagDefs.put("review",   "#1f9d6b");
        tagDefs.put("docs",     "#7d8299");
        tagDefs.put("testing",  "#d99a00");

        tagDefs.forEach((name, color) -> {
            if (!tagRepository.existsByNameIgnoreCase(name)) {
                tagRepository.save(Tag.builder().name(name).color(color).build());
                log.info("Seeded tag #{}", name);
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    //  Sample tasks for the demo user
    // ────────────────────────────────────────────────────────────────

    private void seedSampleTasks(User demoUser, User admin) {
        if (taskRepository.countByOwnerId(demoUser.getId()) > 0) {
            log.info("Sample tasks already present, skipping task seed.");
            return;
        }

        LocalDate today = LocalDate.now();
        List<Task> sampleTasks = List.of(
            task(demoUser, "Set up project structure",
                 "Initialise the Spring Boot project, configure Maven dependencies and lay out the package structure.",
                 today.minusDays(6), Priority.HIGH, TaskStatus.DONE, "feature", "backend"),
            task(demoUser, "Implement user authentication",
                 "Spring Security with JWT: login, registration, and role-based access for USER and ADMIN.",
                 today.plusDays(3), Priority.HIGH, TaskStatus.IN_PROGRESS, "backend", "feature"),
            task(demoUser, "Sketch the dashboard layout",
                 "Paper-first: rough out the dashboard on index cards before touching CSS. Keep it calm and readable.",
                 today.plusDays(5), Priority.MEDIUM, TaskStatus.TODO, "frontend"),
            task(demoUser, "Fix pagination losing filters",
                 "The task list drops its filter parameters when moving between pages. Carry them through the page links.",
                 today.minusDays(1), Priority.HIGH, TaskStatus.TODO, "bug", "urgent"),
            task(demoUser, "Write API documentation",
                 "Document every REST endpoint in Springdoc with request/response examples and auth notes.",
                 today.plusDays(10), Priority.LOW, TaskStatus.TODO, "docs"),
            task(demoUser, "Add integration tests for tasks API",
                 "Cover create, update, status change and delete, including the permission edge cases.",
                 today.plusDays(2), Priority.MEDIUM, TaskStatus.IN_PROGRESS, "testing", "backend"),
            task(demoUser, "Review accessibility of forms",
                 "Labels, focus order and contrast on the auth and task forms.",
                 null, Priority.LOW, TaskStatus.DONE, "review", "frontend")
        );
        taskRepository.saveAll(sampleTasks);

        // One task owned by the admin and assigned to the demo user, to show delegation.
        Task delegated = task(admin, "Prepare release notes for v1.1",
                "Summarise what changed since 1.0 and flag anything that needs a migration step.",
                today.plusDays(4), Priority.MEDIUM, TaskStatus.TODO, "docs", "review");
        delegated.setAssignee(demoUser);
        taskRepository.save(delegated);

        log.info("Seeded {} sample tasks for the demo user.", sampleTasks.size() + 1);
    }

    private Task task(User owner, String title, String description, LocalDate due,
                      Priority priority, TaskStatus status, String... tagNames) {
        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            tagRepository.findByNameIgnoreCase(name).ifPresent(tags::add);
        }
        return Task.builder()
                .title(title)
                .description(description)
                .dueDate(due)
                .priority(priority)
                .status(status)
                .owner(owner)
                .tags(tags)
                .build();
    }
}
