package com.charlie.ikibaho.project.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$") String key,
        @NotBlank @Size(max = 200) String name) {
}
