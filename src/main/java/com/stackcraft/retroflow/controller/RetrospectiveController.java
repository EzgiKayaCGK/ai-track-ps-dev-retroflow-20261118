package com.stackcraft.retroflow.controller;

import com.stackcraft.retroflow.dto.CreateRetrospectiveRequest;
import com.stackcraft.retroflow.dto.RetrospectiveResponse;
import com.stackcraft.retroflow.service.RetrospectiveService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class RetrospectiveController {

    private final RetrospectiveService retrospectiveService;

    public RetrospectiveController(RetrospectiveService retrospectiveService) {
        this.retrospectiveService = retrospectiveService;
    }

    @PostMapping("/api/teams/{teamId}/retrospectives")
    @ResponseStatus(HttpStatus.CREATED)
    public RetrospectiveResponse createRetrospective(
        @RequestHeader("X-User-Name") @NotBlank @Size(max = 100) String requester,
        @PathVariable("teamId") @Positive Long teamId,
        @Valid @RequestBody CreateRetrospectiveRequest request
    ) {
        return retrospectiveService.createRetrospective(teamId, request.title(), requester);
    }

    @GetMapping("/api/teams/{teamId}/retrospectives")
    public List<RetrospectiveResponse> listRetrospectives(
        @RequestHeader("X-User-Name") @NotBlank @Size(max = 100) String requester,
        @PathVariable("teamId") @Positive Long teamId
    ) {
        return retrospectiveService.listRetrospectives(teamId);
    }

    @PutMapping("/api/retrospectives/{id}/close")
    public RetrospectiveResponse closeRetrospective(
        @RequestHeader("X-User-Name") @NotBlank @Size(max = 100) String requester,
        @PathVariable("id") @Positive Long id
    ) {
        return retrospectiveService.closeRetrospective(id, requester);
    }
}
