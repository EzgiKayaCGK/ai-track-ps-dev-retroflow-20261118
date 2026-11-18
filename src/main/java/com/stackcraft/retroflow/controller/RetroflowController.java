package com.stackcraft.retroflow.controller;

import com.stackcraft.retroflow.entity.ActionItem;
import com.stackcraft.retroflow.entity.FeedbackItem;
import com.stackcraft.retroflow.entity.Retrospective;
import com.stackcraft.retroflow.entity.Team;
import com.stackcraft.retroflow.exception.RetroflowException;
import com.stackcraft.retroflow.service.RetrospectiveService;
import com.stackcraft.retroflow.service.TeamService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.stackcraft.retroflow.controller.ApiModels.ActionItemResponse;
import static com.stackcraft.retroflow.controller.ApiModels.FeedbackResponse;
import static com.stackcraft.retroflow.controller.ApiModels.RetrospectiveResponse;
import static com.stackcraft.retroflow.controller.ApiModels.TeamResponse;

@RestController
@RequestMapping("/api/v1")
public class RetroflowController {

    private final TeamService teamService;
    private final RetrospectiveService retrospectiveService;

    public RetroflowController(TeamService teamService, RetrospectiveService retrospectiveService) {
        this.teamService = teamService;
        this.retrospectiveService = retrospectiveService;
    }

    @PostMapping("/teams")
    public TeamResponse createTeam(@RequestBody TeamRequest request) {
        requireRequest(request);
        return TeamResponse.from(teamService.createTeam(request.name(), request.members()));
    }

    @GetMapping("/teams")
    public List<TeamResponse> getTeams() {
        return teamService.getTeams().stream().map(TeamResponse::from).toList();
    }

    @GetMapping("/teams/{teamName}")
    public TeamResponse getTeam(@PathVariable String teamName) {
        return TeamResponse.from(teamService.getTeam(teamName));
    }

    @DeleteMapping("/teams/{teamName}")
    public TeamResponse deleteTeam(@PathVariable String teamName) {
        return TeamResponse.from(teamService.deleteTeam(teamName));
    }

    @PostMapping("/teams/{teamName}/members")
    public TeamResponse addMember(@PathVariable String teamName, @RequestBody MemberRequest request) {
        requireRequest(request);
        return TeamResponse.from(teamService.addMember(teamName, request.name()));
    }

    @PutMapping("/teams/{teamName}/members/{memberName}")
    public TeamResponse editMember(@PathVariable String teamName, @PathVariable String memberName,
                                   @RequestBody MemberRequest request) {
        requireRequest(request);
        return TeamResponse.from(teamService.editMember(teamName, memberName, request.name()));
    }

    @DeleteMapping("/teams/{teamName}/members/{memberName}")
    public TeamResponse removeMember(@PathVariable String teamName, @PathVariable String memberName) {
        return TeamResponse.from(teamService.removeMember(teamName, memberName));
    }

    @PostMapping("/retrospectives")
    public RetrospectiveResponse createRetrospective(@RequestBody Map<String, Object> request) {
        rejectUnexpectedFields(request, Set.of("teamName", "title", "date", "memberName", "status"));
        String status = optionalString(request, "status");
        if (status != null && !"OPEN".equalsIgnoreCase(status)) {
            throw new RetroflowException("A new retrospective must have status OPEN.");
        }
        Team team = teamService.getTeam(requiredString(request, "teamName"));
        return RetrospectiveResponse.from(retrospectiveService.createRetrospective(team.getId(),
                requiredString(request, "title"), requiredString(request, "memberName"),
                requiredString(request, "date")));
    }

    @GetMapping("/retrospectives")
    public List<RetrospectiveResponse> getRetrospectives() {
        return retrospectiveService.getRetrospectives().stream().map(RetrospectiveResponse::from).toList();
    }

    @GetMapping("/retrospectives/team/{teamName}")
    public List<RetrospectiveResponse> getTeamRetrospectives(@PathVariable String teamName) {
        return retrospectiveService.getRetrospectivesForTeam(teamName).stream().map(RetrospectiveResponse::from).toList();
    }

    @GetMapping("/retrospectives/team/{teamName}/open")
    public RetrospectiveResponse getOpenRetrospective(@PathVariable String teamName) {
        return RetrospectiveResponse.from(retrospectiveService.getOpenRetrospective(teamName));
    }

    @GetMapping("/retrospectives/{title}")
    public RetrospectiveResponse getRetrospective(@PathVariable String title) {
        return RetrospectiveResponse.from(retrospectiveService.getRetrospective(title));
    }

    @PostMapping("/retrospectives/{title}/close")
    public RetrospectiveResponse closeRetrospective(@PathVariable String title, @RequestBody MemberIdentity request) {
        requireRequest(request);
        return RetrospectiveResponse.from(retrospectiveService.closeRetrospective(title, request.memberName()));
    }

    @GetMapping("/retrospectives/{title}/feedback")
    public List<FeedbackResponse> getFeedbackItems(@PathVariable String title) {
        Retrospective retrospective = retrospectiveService.getRetrospective(title);
        return retrospectiveService.getFeedbackItems(retrospective.getId()).stream().map(FeedbackResponse::from).toList();
    }

    @PostMapping("/retrospectives/{title}/feedback")
    public FeedbackResponse addFeedbackItem(@PathVariable String title, @RequestBody FeedbackRequest request) {
        requireRequest(request);
        Retrospective retrospective = retrospectiveService.getRetrospective(title);
        return FeedbackResponse.from(retrospectiveService.addFeedbackItem(retrospective.getId(), request.content(),
                request.type(), request.submittedBy()));
    }

    @PutMapping("/feedback/{feedbackItemId}")
    public FeedbackResponse modifyFeedbackItem(@PathVariable Long feedbackItemId,
                                               @RequestBody Map<String, Object> request) {
        rejectUnexpectedFields(request, Set.of("content", "type", "memberName"));
        requireOneMutableField(request, Set.of("content", "type"));
        FeedbackItem item = retrospectiveService.modifyFeedbackItem(feedbackItemId, optionalString(request, "content"),
                optionalString(request, "type"), requiredString(request, "memberName"));
        return FeedbackResponse.from(item);
    }

    @DeleteMapping("/feedback/{feedbackItemId}")
    public FeedbackResponse deleteFeedbackItem(@PathVariable Long feedbackItemId,
                                               @RequestParam String memberName) {
        return FeedbackResponse.from(retrospectiveService.deleteFeedbackItem(feedbackItemId, memberName));
    }

    @GetMapping("/retrospectives/{title}/action-items")
    public List<ActionItemResponse> getActionItems(@PathVariable String title,
                                                   @RequestParam String memberName,
                                                   @RequestParam(required = false) String priority,
                                                   @RequestParam(required = false) String completed) {
        Retrospective retrospective = retrospectiveService.getRetrospective(title);
        return retrospectiveService.getActionItems(retrospective.getId(), memberName, priority,
                        parseBooleanFilter(completed))
                .stream().map(ActionItemResponse::from).toList();
    }

    @PostMapping("/retrospectives/{title}/action-items")
    public ActionItemResponse addActionItem(@PathVariable String title, @RequestBody Map<String, Object> request) {
        rejectUnexpectedFields(request, Set.of("content", "priority", "submittedBy", "completed"));
        ActionItem item = retrospectiveService.addActionItem(retrospectiveService.getRetrospective(title).getId(),
                requiredString(request, "content"), requiredString(request, "priority"),
                requiredString(request, "submittedBy"), optionalBoolean(request, "completed", false));
        return ActionItemResponse.from(item);
    }

    @GetMapping("/action-items/{actionItemId}")
    public ActionItemResponse getActionItem(@PathVariable Long actionItemId,
                                            @RequestParam String memberName) {
        return ActionItemResponse.from(retrospectiveService.getActionItem(actionItemId, memberName));
    }

    @PutMapping("/action-items/{actionItemId}")
    public ActionItemResponse modifyActionItem(@PathVariable Long actionItemId,
                                               @RequestBody Map<String, Object> request) {
        rejectUnexpectedFields(request, Set.of("content", "priority", "completed", "memberName"));
        requireOneMutableField(request, Set.of("content", "priority", "completed"));
        ActionItem item = retrospectiveService.modifyActionItem(actionItemId, optionalString(request, "content"),
                optionalString(request, "priority"), optionalBoolean(request, "completed", null),
                requiredString(request, "memberName"));
        return ActionItemResponse.from(item);
    }

    @PostMapping("/action-items/{actionItemId}/complete")
    public ActionItemResponse completeActionItem(@PathVariable Long actionItemId,
                                                 @RequestBody MemberIdentity request) {
        requireRequest(request);
        return ActionItemResponse.from(retrospectiveService.completeActionItem(actionItemId, request.memberName()));
    }

    @DeleteMapping("/action-items/{actionItemId}")
    public ActionItemResponse deleteActionItem(@PathVariable Long actionItemId,
                                               @RequestParam String memberName) {
        return ActionItemResponse.from(retrospectiveService.deleteActionItem(actionItemId, memberName));
    }

    private void rejectUnexpectedFields(Map<String, Object> request, Set<String> allowedFields) {
        if (request == null || !allowedFields.containsAll(request.keySet())) {
            throw new RetroflowException("The request contains fields that cannot be changed.");
        }
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new RetroflowException("The request body is required.");
        }
    }

    private void requireOneMutableField(Map<String, Object> request, Set<String> mutableFields) {
        if (request.keySet().stream().noneMatch(mutableFields::contains)) {
            throw new RetroflowException("At least one editable field must be supplied.");
        }
    }

    private String requiredString(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new RetroflowException(field + " is required.");
        }
        return text;
    }

    private String optionalString(Map<String, Object> request, String field) {
        if (request.containsKey(field) && request.get(field) == null) {
            throw new RetroflowException(field + " must be a string.");
        }
        Object value = request.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new RetroflowException(field + " must be a string.");
        }
        return text;
    }

    private Boolean optionalBoolean(Map<String, Object> request, String field, Boolean defaultValue) {
        if (request.containsKey(field) && request.get(field) == null) {
            throw new RetroflowException(field + " must be a boolean.");
        }
        Object value = request.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean result)) {
            throw new RetroflowException(field + " must be a boolean.");
        }
        return result;
    }

    private Boolean parseBooleanFilter(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new RetroflowException("Invalid completed filter value: " + value);
    }

    public record TeamRequest(String name, List<String> members) {
    }

    public record MemberRequest(String name) {
    }

    public record MemberIdentity(String memberName) {
    }

    public record FeedbackRequest(String content, String type, String submittedBy) {
    }
}
