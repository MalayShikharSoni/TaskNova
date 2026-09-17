package com.tasknova.entity;

import com.tasknova.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered user in the TaskNova system.
 * A user can own multiple tasks and be assigned tasks by an admin.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = "email", name = "uk_users_email"),
            @UniqueConstraint(columnNames = "username", name = "uk_users_username")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"ownedTasks", "assignedTasks"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Unique display name */
    @Column(nullable = false, length = 50)
    private String username;

    /** Used as login identifier */
    @Column(nullable = false, length = 100)
    private String email;

    /** BCrypt-encoded password */
    @Column(nullable = false)
    private String password;

    /** Role-based access control */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Role role = Role.USER;

    /** Soft-disable without deleting the account */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Tasks this user created/owns */
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Task> ownedTasks = new ArrayList<>();

    /** Tasks assigned to this user by an admin */
    @OneToMany(mappedBy = "assignee")
    @Builder.Default
    private List<Task> assignedTasks = new ArrayList<>();
}
