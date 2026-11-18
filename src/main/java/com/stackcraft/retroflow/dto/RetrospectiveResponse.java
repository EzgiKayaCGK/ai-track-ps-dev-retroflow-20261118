package com.stackcraft.retroflow.dto;

import com.stackcraft.retroflow.entity.RetrospectiveStatus;

import java.time.LocalDate;

public record RetrospectiveResponse(
    Long id,
    Long teamId,
    String title,
    LocalDate date,
    RetrospectiveStatus status
) {
}
