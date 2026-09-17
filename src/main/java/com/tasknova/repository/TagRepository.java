package com.tasknova.repository;

import com.tasknova.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Tag> findByNameIgnoreCase(String name);
}
