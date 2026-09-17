package com.tasknova.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A label/tag that can be attached to multiple tasks.
 * Many-to-many with {@link Task}.
 */
@Entity
@Table(name = "tags",
        uniqueConstraints = @UniqueConstraint(columnNames = "name", name = "uk_tags_name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Unique tag name e.g. "bug", "feature", "urgent" */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * Hex color code for the tag badge in the UI.
     * e.g. "#4f46e5"
     */
    @Column(nullable = false, length = 7)
    @Builder.Default
    private String color = "#4f46e5";
}
