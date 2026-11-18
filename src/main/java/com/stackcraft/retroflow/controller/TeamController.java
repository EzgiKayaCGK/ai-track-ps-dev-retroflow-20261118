package com.stackcraft.retroflow.controller;

import com.stackcraft.retroflow.dto.CreateTeamRequest;
import com.stackcraft.retroflow.dto.TeamResponse;
import com.stackcraft.retroflow.service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@Validated
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(
        @RequestHeader("X-User-Name") @NotBlank @Size(max = 100) String requester,
        @Valid @RequestBody CreateTeamRequest request
    ) {
        return teamService.createTeam(request.name(), request.members());
    }

    @GetMapping("/{id}")
    public TeamResponse getTeam(
        @RequestHeader("X-User-Name") @NotBlank @Size(max = 100) String requester,
        @PathVariable("id") @Positive Long id
    ) {
        return teamService.getTeam(id);
    }
}
