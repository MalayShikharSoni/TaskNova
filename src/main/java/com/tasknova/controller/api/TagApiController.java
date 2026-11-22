package com.tasknova.controller.api;

import com.tasknova.dto.tag.TagDto;
import com.tasknova.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/tags: the tag vocabulary accepted by tagNames on task endpoints. */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagApiController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TagDto>> getTags() {
        return ResponseEntity.ok(taskService.getAllTags());
    }
}
