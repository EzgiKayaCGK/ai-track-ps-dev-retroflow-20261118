package com.stackcraft.retroflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRetrospectiveRequest(@NotBlank @Size(max = 200) String title) {
}
