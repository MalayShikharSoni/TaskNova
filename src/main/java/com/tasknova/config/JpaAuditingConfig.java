package com.tasknova.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so that @CreatedDate and
 * @LastModifiedDate annotations on entities are auto-populated.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
