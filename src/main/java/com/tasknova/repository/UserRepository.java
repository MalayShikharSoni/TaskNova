package com.tasknova.repository;

import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link User} entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** All users with a given role, paginated */
    Page<User> findAllByRole(Role role, Pageable pageable);

    /** Search users by username or email fragment (case-insensitive) */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    /** Count active users */
    long countByActiveTrue();

    /** Count users by role */
    long countByRole(Role role);
}
