package com.tasknova.entity.enums;

/**
 * User roles within the TaskNova system.
 * Spring Security prefixes these with "ROLE_" automatically
 * when stored via GrantedAuthority.
 */
public enum Role {
    USER,
    ADMIN
}
