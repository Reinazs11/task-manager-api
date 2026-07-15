package com.renan.taskmanager.tasks.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/projects}.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name
) {}
