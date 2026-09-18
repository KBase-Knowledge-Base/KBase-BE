package com.kbase.tag.mapper;

import java.util.Objects;

import com.kbase.tag.dto.response.TagResponse;
import com.kbase.tag.entity.Tag;

import org.springframework.stereotype.Component;

/** Maps a tag entity to its public REST representation. */
@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt());
    }
}
