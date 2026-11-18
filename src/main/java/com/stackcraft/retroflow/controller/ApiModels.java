package com.stackcraft.retroflow.controller;

import com.stackcraft.retroflow.entity.ActionItem;
import com.stackcraft.retroflow.entity.FeedbackItem;
import com.stackcraft.retroflow.entity.Retrospective;
import com.stackcraft.retroflow.entity.Team;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public record TeamResponse(Long id, String name, List<String> members) {
        static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(), List.copyOf(team.getMembers()));
        }
    }

    public record RetrospectiveResponse(Long id, String title, LocalDate date, String status, String teamName) {
        static RetrospectiveResponse from(Retrospective retrospective) {
            return new RetrospectiveResponse(retrospective.getId(), retrospective.getTitle(),
                    retrospective.getDate(), retrospective.getStatus().name(), retrospective.getTeam().getName());
        }
    }

    public record FeedbackResponse(Long id, String content, String type, String submittedBy, String retrospectiveTitle) {
        static FeedbackResponse from(FeedbackItem item) {
            return new FeedbackResponse(item.getId(), item.getContent(), item.getType().name(), item.getSubmittedBy(),
                    item.getRetrospective().getTitle());
        }
    }

    public record ActionItemResponse(Long id, String content, String priority, boolean completed,
                                     String submittedBy, String retrospectiveTitle) {
        static ActionItemResponse from(ActionItem item) {
            return new ActionItemResponse(item.getId(), item.getContent(), item.getPriority().name(),
                    item.isCompleted(), item.getSubmittedBy(), item.getRetrospective().getTitle());
        }
    }

    public record ErrorResponse(String error, int status, Instant timestamp) {
    }
}
