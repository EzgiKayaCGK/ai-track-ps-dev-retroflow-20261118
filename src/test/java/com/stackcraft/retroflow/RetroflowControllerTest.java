package com.stackcraft.retroflow;

import com.stackcraft.retroflow.controller.RetrospectiveController;
import com.stackcraft.retroflow.controller.TeamController;
import com.stackcraft.retroflow.dto.RetrospectiveResponse;
import com.stackcraft.retroflow.dto.TeamResponse;
import com.stackcraft.retroflow.entity.RetrospectiveStatus;
import com.stackcraft.retroflow.exception.ConflictException;
import com.stackcraft.retroflow.exception.ForbiddenException;
import com.stackcraft.retroflow.exception.ResourceNotFoundException;
import com.stackcraft.retroflow.exception.RetroflowException;
import com.stackcraft.retroflow.service.RetrospectiveService;
import com.stackcraft.retroflow.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TeamController.class, RetrospectiveController.class})
class RetroflowControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TeamService teamService;

    @MockBean
    private RetrospectiveService retrospectiveService;

    @Test
    void createsAndRetrievesTeam() throws Exception {
        TeamResponse team = new TeamResponse(1L, "Team", List.of("Alice"));
        when(teamService.createTeam("Team", List.of("Alice"))).thenReturn(team);
        when(teamService.getTeam(1L)).thenReturn(team);

        mvc.perform(post("/api/teams").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team\",\"members\":[\"Alice\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.members[0]").value("Alice"));
        mvc.perform(get("/api/teams/1").header("X-User-Name", "Alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Team"));
    }

    @Test
    void createsListsAndClosesRetrospective() throws Exception {
        RetrospectiveResponse open = new RetrospectiveResponse(
            2L, 1L, "Sprint", LocalDate.of(2026, 11, 18), RetrospectiveStatus.OPEN);
        RetrospectiveResponse closed = new RetrospectiveResponse(
            2L, 1L, "Sprint", open.date(), RetrospectiveStatus.CLOSED);
        when(retrospectiveService.createRetrospective(1L, "Sprint", "Alice")).thenReturn(open);
        when(retrospectiveService.listRetrospectives(1L)).thenReturn(List.of(open));
        when(retrospectiveService.closeRetrospective(2L, "Alice")).thenReturn(closed);

        mvc.perform(post("/api/teams/1/retrospectives").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Sprint\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/teams/1/retrospectives").header("X-User-Name", "Alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(2));
        mvc.perform(put("/api/retrospectives/2/close").header("X-User-Name", "Alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
        verify(retrospectiveService).closeRetrospective(2L, "Alice");
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/teams").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"members\":[\"\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/teams").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team\",\"members\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/teams/1/retrospectives").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/teams/1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Missing required header: X-User-Name"));
        mvc.perform(get("/api/teams/1").header("X-User-Name", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/teams/0").header("X-User-Name", "Alice"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/teams/not-a-number").header("X-User-Name", "Alice"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/teams").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void mapsServiceErrors() throws Exception {
        when(teamService.getTeam(1L)).thenThrow(new ResourceNotFoundException("Team not found"));
        when(teamService.createTeam("Team", List.of("Alice")))
            .thenThrow(new ConflictException("Team already exists"));
        when(retrospectiveService.createRetrospective(1L, "Sprint", "Alice"))
            .thenThrow(new ForbiddenException("Not a team member"));
        when(retrospectiveService.closeRetrospective(2L, "Alice"))
            .thenThrow(new RetroflowException("Retrospective already closed"));

        mvc.perform(get("/api/teams/1").header("X-User-Name", "Alice"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Team not found"));
        mvc.perform(post("/api/teams").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team\",\"members\":[\"Alice\"]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Team already exists"));
        mvc.perform(post("/api/teams/1/retrospectives").header("X-User-Name", "Alice")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Sprint\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Not a team member"));
        mvc.perform(put("/api/retrospectives/2/close").header("X-User-Name", "Alice"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Retrospective already closed"));
    }
}
