package com.renan.taskmanager.tasks.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/projects}.
 */
@Schema(name = "CreateProjectRequest", description = "Payload to create a new project")
public record CreateProjectRequest(
        @Schema(description = "Project name (1-200 chars)", example = "Q3 roadmap")
        @NotBlank @Size(max = 200) String name
) {}
