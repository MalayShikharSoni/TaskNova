package com.tasknova.dto.tag;

import com.tasknova.entity.Tag;

/** Lightweight tag view (name + badge colour) for pickers and API clients. */
public record TagDto(Long id, String name, String color) {

    public static TagDto from(Tag tag) {
        return new TagDto(tag.getId(), tag.getName(), tag.getColor());
    }
}
