package com.stackcraft.retroflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackcraft.retroflow.entity.ActionItem;
import com.stackcraft.retroflow.entity.FeedbackItem;
import com.stackcraft.retroflow.entity.Retrospective;
import com.stackcraft.retroflow.entity.RetrospectiveStatus;
import com.stackcraft.retroflow.entity.Team;
import com.stackcraft.retroflow.exception.ResourceNotFoundException;
import com.stackcraft.retroflow.exception.RetroflowException;
import com.stackcraft.retroflow.service.RetrospectiveService;
import com.stackcraft.retroflow.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RetroflowBusinessRulesMatrixTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private RetrospectiveService retrospectiveService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teamNames_areRequiredAndLimitedTo256Characters() {
        assertThatThrownBy(() -> teamService.createTeam(" \t ", List.of("Alice")))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("T".repeat(257), List.of("Alice")))
                .isInstanceOf(RetroflowException.class);
        assertThat(teamService.createTeam("😀".repeat(256), List.of("Alice")).getName()).hasSize(512);
    }

    @Test
    void teamNames_areTrimmedBeforeStorage() {
        String name = "Team " + token();
        assertThat(teamService.createTeam("  " + name + "  ", List.of("Alice")).getName()).isEqualTo(name);
    }

    @Test
    void teamNames_areUniqueWithoutCaseSensitivity() {
        String name = "Unique " + token();
        teamService.createTeam(name, List.of("Alice"));
        assertThatThrownBy(() -> teamService.createTeam(name.toUpperCase(), List.of("Bob")))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void teams_requireAtLeastOneMember() {
        assertThatThrownBy(() -> teamService.createTeam("No members " + token(), List.of()))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Null members " + token(), null))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void memberNames_areValidatedWithoutTrimming() {
        assertThatThrownBy(() -> teamService.createTeam("Bad member " + token(), List.of(" ")))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Padded member " + token(), List.of(" Alice ")))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Unicode member " + token(), List.of("\u2003Alice\u2003")))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Long member " + token(), List.of("M".repeat(257))))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void memberNames_areUniqueWithinATeamIgnoringCase() {
        assertThatThrownBy(() -> teamService.createTeam("Dup members " + token(), List.of("Alice", "ALICE")))
                .isInstanceOf(RetroflowException.class);
        Team otherTeam = teamService.createTeam("Other team " + token(), List.of("Alice"));
        assertThat(otherTeam.getMembers()).containsExactly("Alice");
    }

    @Test
    void teamLookupAndMemberListOperations_areCaseInsensitiveAndNeedNoMemberIdentity() {
        Team team = team();
        assertThat(teamService.getTeam(team.getName().toUpperCase()).getId()).isEqualTo(team.getId());
        teamService.addMember(team.getName().toUpperCase(), "Carol");
        teamService.editMember(team.getName(), "CAROL", "Chris");
        assertThat(teamService.getTeam(team.getName()).getMembers()).contains("Chris");
        teamService.removeMember(team.getName(), "chris");
        assertThat(teamService.getTeam(team.getName()).getMembers()).doesNotContain("Chris");
    }

    @Test
    void teamDeletion_softDeletesItsChildrenAndMakesThemUnavailable() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        FeedbackItem feedback = retrospectiveService.addFeedbackItem(retrospective.getId(), "Keep it up",
                "WENT_WELL", "Alice");
        ActionItem action = retrospectiveService.addActionItem(retrospective.getId(), "Fix it", "HIGH", "Bob");
        teamService.deleteTeam(team.getName().toUpperCase());
        assertThat(team.isDeleted()).isTrue();
        assertThat(retrospective.isDeleted()).isTrue();
        assertThat(feedback.isDeleted()).isTrue();
        assertThat(action.isDeleted()).isTrue();
        assertThatThrownBy(() -> teamService.getTeam(team.getName())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> retrospectiveService.getRetrospective(retrospective.getTitle()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void apiResponses_doNotExposeDeletionFlags() throws Exception {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        FeedbackItem feedback = retrospectiveService.addFeedbackItem(retrospective.getId(), "Visible feedback",
                "WENT_WELL", "Alice");
        ActionItem action = actionItem(retrospective);
        mockMvc.perform(get("/api/v1/teams/{name}", team.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").doesNotExist())
                .andExpect(jsonPath("$.deleted").doesNotExist());
        mockMvc.perform(get("/api/v1/retrospectives/{title}", retrospective.getTitle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").doesNotExist())
                .andExpect(jsonPath("$.deleted").doesNotExist());
        mockMvc.perform(get("/api/v1/retrospectives/{title}/feedback", retrospective.getTitle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDeleted").doesNotExist())
                .andExpect(jsonPath("$[0].deleted").doesNotExist());
        mockMvc.perform(get("/api/v1/action-items/{id}", action.getId()).param("memberName", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").doesNotExist())
                .andExpect(jsonPath("$.deleted").doesNotExist());
    }

    @Test
    void deletedRecords_areNotListedOrTargetable() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        teamService.deleteTeam(team.getName());
        assertThat(teamService.getTeams()).doesNotContain(team);
        assertThat(retrospectiveService.getRetrospectives()).doesNotContain(retrospective);
        assertThatThrownBy(() -> retrospectiveService.getFeedbackItems(retrospective.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void memberListChanges_doNotRewriteHistoricalFeedback() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        FeedbackItem feedback = retrospectiveService.addFeedbackItem(retrospective.getId(), "Recorded by Alice",
                "WENT_WELL", "Alice");
        teamService.editMember(team.getName(), "Alice", "Alicia");
        assertThat(feedback.getSubmittedBy()).isEqualTo("Alice");
        assertThat(retrospectiveService.getFeedbackItems(retrospective.getId())).contains(feedback);
        teamService.removeMember(team.getName(), "Alicia");
        assertThat(feedback.getSubmittedBy()).isEqualTo("Alice");
    }

    @Test
    void removingTheLastMember_isRejected() {
        Team team = teamService.createTeam("Solo " + token(), List.of("Alice"));
        assertThatThrownBy(() -> teamService.removeMember(team.getName(), "Alice"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void memberOnlyOperations_validateCurrentMembership() {
        Team team = team();
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "Unauthorized " + token(),
                "Mallory", "2026-04-03T00:00:00Z")).isInstanceOf(RetroflowException.class);
        Retrospective retrospective = retrospective(team);
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), "Invalid submitter",
                "WENT_WELL", "Mallory")).isInstanceOf(RetroflowException.class);
    }

    @Test
    void retrospectiveTitles_areRequiredAndLimitedTo256Characters() {
        Team team = team();
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), " ", "Alice",
                "2026-04-03T00:00:00Z")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "T".repeat(257), "Alice",
                "2026-04-03T00:00:00Z")).isInstanceOf(RetroflowException.class);
    }

    @Test
    void retrospectiveTitles_areGloballyUniqueIgnoringCase() {
        Team first = team();
        Team second = team();
        String title = "Shared title " + token();
        retrospectiveService.createRetrospective(first.getId(), title, "Alice", "2026-04-03T00:00:00Z");
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(second.getId(), title.toUpperCase(), "Alice",
                "2026-04-04T00:00:00Z")).isInstanceOf(RetroflowException.class);
    }

    @Test
    void retrospectiveDates_mustBeUtcIso8601AndMayBeInTheFuture() {
        Team team = team();
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "Missing date " + token(),
                "Alice", null)).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "Invalid date " + token(),
                "Alice", "not-a-date")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "Local date " + token(),
                "Alice", "2026-04-03T00:00:00+02:00")).isInstanceOf(RetroflowException.class);
        Retrospective future = retrospectiveService.createRetrospective(team.getId(), "Future " + token(), "Alice",
                "2999-12-31T23:59:59Z");
        assertThat(future.getDate()).isEqualTo(LocalDate.of(2999, 12, 31));
    }

    @Test
    void retrospectiveDates_doNotNeedToBeUnique() {
        String date = "2026-04-03T00:00:00Z";
        Retrospective first = retrospectiveService.createRetrospective(team().getId(), "Same date A " + token(),
                "Alice", date);
        Retrospective second = retrospectiveService.createRetrospective(team().getId(), "Same date B " + token(),
                "Alice", date);
        assertThat(first.getDate()).isEqualTo(second.getDate());
    }

    @Test
    void retrospectiveMustBelongToAnActiveTeam() {
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(Long.MAX_VALUE, "No team " + token(),
                "Alice", "2026-04-03T00:00:00Z")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anyCurrentTeamMemberMayCreateARetrospective() {
        Team team = team();
        Retrospective created = retrospectiveService.createRetrospective(team.getId(), "By Bob " + token(), "Bob",
                "2026-04-03T00:00:00Z");
        assertThat(created.getTeam()).isEqualTo(team);
    }

    @Test
    void newRetrospectivesStartOpenAndClientsCannotCreateThemClosed() throws Exception {
        Team team = team();
        Retrospective created = retrospective(team);
        assertThat(created.getStatus()).isEqualTo(RetrospectiveStatus.OPEN);
        mockMvc.perform(post("/api/v1/retrospectives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("teamName", team.getName(), "title", "Closed creation " + token(),
                                "date", "2026-04-03T00:00:00Z", "memberName", "Alice", "status", "CLOSED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTeamCannotHaveTwoOpenRetrospectives() {
        Team team = team();
        retrospective(team);
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(team.getId(), "Second open " + token()))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void anyCurrentMemberCanCloseARetrospectiveButItCannotBeClosedTwice() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        assertThat(retrospectiveService.closeRetrospective(retrospective.getId(), "Bob").getStatus())
                .isEqualTo(RetrospectiveStatus.CLOSED);
        assertThatThrownBy(() -> retrospectiveService.closeRetrospective(retrospective.getId(), "Alice"))
                .isInstanceOf(RetroflowException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void retrospectiveRetrievalSupportsAllScopesAndCaseInsensitiveTitles() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        assertThat(retrospectiveService.getRetrospective(retrospective.getTitle().toUpperCase()).getId())
                .isEqualTo(retrospective.getId());
        assertThat(retrospectiveService.getRetrospectivesForTeam(team.getName().toUpperCase()))
                .containsExactly(retrospective);
        assertThat(retrospectiveService.getOpenRetrospective(team.getName())).isEqualTo(retrospective);
        assertThat(retrospectiveService.getRetrospectives()).contains(retrospective);
        assertThatThrownBy(() -> retrospectiveService.getRetrospective("missing " + token()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retrospectiveListsSortByDateDescendingThenIdAscendingAndAreUnpaginated() {
        Team firstTeam = team();
        Team secondTeam = team();
        Retrospective old = retrospectiveService.createRetrospective(firstTeam.getId(), "Old " + token(), "Alice",
                "2026-01-01T00:00:00Z");
        Retrospective tie1 = retrospectiveService.createRetrospective(secondTeam.getId(), "Tie 1 " + token(), "Alice",
                "2026-03-01T00:00:00Z");
        Team thirdTeam = team();
        Retrospective tie2 = retrospectiveService.createRetrospective(thirdTeam.getId(), "Tie 2 " + token(), "Alice",
                "2026-03-01T00:00:00Z");
        assertThat(retrospectiveService.getRetrospectives()).containsSubsequence(tie1, tie2, old);

        for (int i = 0; i < 15; i++) {
            retrospective(team());
        }
        assertThat(retrospectiveService.getRetrospectives()).hasSizeGreaterThan(15);
    }

    @Test
    void emptyCollectionRetrievalReturnsAnEmptyList() {
        Team team = team();
        assertThat(retrospectiveService.getRetrospectivesForTeam(team.getName())).isEmpty();
        assertThat(retrospectiveService.getFeedbackItems(ensureRetro(team).getId())).isEmpty();
    }

    @Test
    void feedbackAndActionItemContent_requireValidUnpaddedTextOfAtMost256Characters() {
        Retrospective retrospective = retrospective(team());
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), " ", "WENT_WELL",
                "Alice")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), " padded ",
                "WENT_WELL", "Alice")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), "\u2003padded\u2003",
                "WENT_WELL", "Alice")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), "x".repeat(257),
                "WENT_WELL", "Alice")).isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.addActionItem(retrospective.getId(), " padded ", "LOW",
                "Alice")).isInstanceOf(RetroflowException.class);
        assertThat(retrospectiveService.addFeedbackItem(retrospective.getId(), "😀".repeat(256), "WENT_WELL", "Alice"))
                .isNotNull();
    }

    @Test
    void feedbackTypeMustBeSupportedAndActionItemsUseTheirOwnOperation() {
        Retrospective retrospective = retrospective(team());
        for (String invalidType : List.of("ACTION_ITEM", "OTHER")) {
            assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(retrospective.getId(), "valid", invalidType,
                    "Alice")).isInstanceOf(RetroflowException.class);
        }
        assertThat(retrospectiveService.addFeedbackItem(retrospective.getId(), "valid", "WENT_WELL", "Alice"))
                .isNotNull();
    }

    @Test
    void feedbackCanBeAddedOnlyByCurrentMembersToOpenRetrospectives() {
        Retrospective open = retrospective(team());
        assertThat(retrospectiveService.addFeedbackItem(open.getId(), "Valid", "WENT_WELL", "Bob")).isNotNull();
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(open.getId(), "Valid", "WENT_WELL",
                "Unknown")).isInstanceOf(RetroflowException.class);
        retrospectiveService.closeRetrospective(open.getId());
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(open.getId(), "Late", "WENT_WELL", "Alice"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void feedbackRequiresAnAvailableParentRetrospective() {
        assertThatThrownBy(() -> retrospectiveService.addFeedbackItem(Long.MAX_VALUE, "Valid", "WENT_WELL", "Alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void feedbackCanBeModifiedByAnyCurrentMemberOnlyWhileOpenAndCannotChangeItsOwner() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        FeedbackItem item = retrospectiveService.addFeedbackItem(retrospective.getId(), "Before", "WENT_WELL", "Alice");
        assertThat(retrospectiveService.modifyFeedbackItem(item.getId(), "After", "NEEDS_IMPROVEMENT", "Bob"))
                .satisfies(updated -> {
                    assertThat(updated.getContent()).isEqualTo("After");
                    assertThat(updated.getType().name()).isEqualTo("NEEDS_IMPROVEMENT");
                    assertThat(updated.getSubmittedBy()).isEqualTo("Alice");
                    assertThat(updated.getRetrospective()).isEqualTo(retrospective);
                });
        retrospectiveService.closeRetrospective(retrospective.getId());
        assertThatThrownBy(() -> retrospectiveService.modifyFeedbackItem(item.getId(), "Closed", null, "Bob"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void feedbackCannotBeDeletedAfterClosureAndCanBeSoftDeletedWhileOpen() {
        Team team = team();
        Retrospective retrospective = retrospective(team);
        FeedbackItem removed = retrospectiveService.addFeedbackItem(retrospective.getId(), "Remove", "WENT_WELL",
                "Alice");
        assertThat(retrospectiveService.deleteFeedbackItem(removed.getId(), "Bob").isDeleted()).isTrue();
        FeedbackItem closedItem = retrospectiveService.addFeedbackItem(retrospective.getId(), "Keep", "WENT_WELL",
                "Alice");
        retrospectiveService.closeRetrospective(retrospective.getId());
        assertThatThrownBy(() -> retrospectiveService.deleteFeedbackItem(closedItem.getId(), "Bob"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void feedbackRetrievalReturnsOnlyTheRetrospectiveFeedbackCollection() {
        Retrospective retrospective = retrospective(team());
        FeedbackItem feedback = retrospectiveService.addFeedbackItem(retrospective.getId(), "Feedback", "WENT_WELL",
                "Alice");
        FeedbackItem secondFeedback = retrospectiveService.addFeedbackItem(retrospective.getId(), "More feedback",
                "NEEDS_IMPROVEMENT", "Bob");
        retrospectiveService.addActionItem(retrospective.getId(), "Action", "HIGH", "Bob");
        assertThat(retrospectiveService.getFeedbackItems(retrospective.getId()))
                .containsExactlyInAnyOrder(feedback, secondFeedback);
        assertThat(retrospectiveService.getFeedbackItems(retrospective.getId()).stream()
                .map(FeedbackItem::getType).toList()).doesNotContain(com.stackcraft.retroflow.entity.FeedbackItemType.ACTION_ITEM);
    }

    @Test
    void actionItemsAreSeparateStartIncompleteAndRequireMemberAndPriority() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = retrospectiveService.addActionItem(retrospective.getId(), "Action", "MEDIUM", "Alice");
        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getSubmittedBy()).isEqualTo("Alice");
        assertThat(item.getType().name()).isEqualTo("ACTION_ITEM");
        for (String priority : List.of("", "URGENT")) {
            assertThatThrownBy(() -> retrospectiveService.addActionItem(retrospective.getId(), "Other", priority,
                    "Bob")).isInstanceOf(RetroflowException.class);
        }
        assertThatThrownBy(() -> retrospectiveService.addActionItem(retrospective.getId(), "Other", "LOW", "Unknown"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void actionItemCreationRejectsExplicitCompletedTrue() {
        Retrospective retrospective = retrospective(team());
        assertThatThrownBy(() -> retrospectiveService.addActionItem(retrospective.getId(), "Action", "LOW", "Alice",
                true)).isInstanceOf(RetroflowException.class);
    }

    @Test
    void actionItemsCanBeAddedOnlyToOpenRetrospectives() {
        Retrospective retrospective = retrospective(team());
        assertThat(retrospectiveService.addActionItem(retrospective.getId(), "Open", "LOW", "Alice")).isNotNull();
        retrospectiveService.closeRetrospective(retrospective.getId());
        assertThatThrownBy(() -> retrospectiveService.addActionItem(retrospective.getId(), "Closed", "LOW", "Alice"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void actionItemContentAndPriorityAreEditableWhileOpen() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = retrospectiveService.addActionItem(retrospective.getId(), "Before", "LOW", "Alice");
        ActionItem changed = retrospectiveService.modifyActionItem(item.getId(), "After", "HIGH", null, "Bob");
        assertThat(changed.getContent()).isEqualTo("After");
        assertThat(changed.getPriority().name()).isEqualTo("HIGH");
    }

    @Test
    void actionItemUpdatesRejectFieldsOutsideTheEditableSet() throws Exception {
        ActionItem item = actionItem(retrospective(team()));
        mockMvc.perform(put("/api/v1/action-items/{id}", item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("submittedBy", "Mallory", "memberName", "Alice"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").isNotEmpty());
    }

    @Test
    void onlyCurrentMembersCanCompleteActionItemsAndCompletionCannotBeReversedOrRepeated() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = actionItem(retrospective);
        assertThatThrownBy(() -> retrospectiveService.completeActionItem(item.getId(), "Mallory"))
                .isInstanceOf(RetroflowException.class);
        assertThat(retrospectiveService.completeActionItem(item.getId(), "Bob").isCompleted()).isTrue();
        assertThatThrownBy(() -> retrospectiveService.uncompleteActionItem(item.getId()))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.completeActionItem(item.getId(), "Alice"))
                .isInstanceOf(RetroflowException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void closedRetrospectivePreventsAllActionItemMutationsIncludingCompletionAndDeletion() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = actionItem(retrospective);
        retrospectiveService.closeRetrospective(retrospective.getId());
        assertThatThrownBy(() -> retrospectiveService.modifyActionItem(item.getId(), "Changed", null, null, "Alice"))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.completeActionItem(item.getId(), "Alice"))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.deleteActionItem(item.getId(), "Alice"))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void actionItemsCanBeDeletedByCurrentMembersWhileOpen() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = actionItem(retrospective);
        assertThat(retrospectiveService.deleteActionItem(item.getId(), "Bob").isDeleted()).isTrue();
        assertThat(retrospectiveService.getActionItems(retrospective.getId(), "Alice", null, null)).isEmpty();
    }

    @Test
    void actionItemRetrievalRequiresCurrentMembershipForCollectionAndSingleItem() {
        Retrospective retrospective = retrospective(team());
        ActionItem item = actionItem(retrospective);
        assertThat(retrospectiveService.getActionItems(retrospective.getId(), "Bob", null, null)).containsExactly(item);
        assertThat(retrospectiveService.getActionItem(item.getId(), "Alice")).isEqualTo(item);
        assertThatThrownBy(() -> retrospectiveService.getActionItems(retrospective.getId(), "Nobody", null, null))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.getActionItem(item.getId(), "Nobody"))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> retrospectiveService.getActionItem(Long.MAX_VALUE, "Alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void actionItemFiltersCanBeCombinedAndInvalidValuesAreIdentified() {
        Retrospective retrospective = retrospective(team());
        ActionItem high = retrospectiveService.addActionItem(retrospective.getId(), "High", "HIGH", "Alice");
        retrospectiveService.addActionItem(retrospective.getId(), "Low", "LOW", "Bob");
        retrospectiveService.completeActionItem(high.getId(), "Alice");
        assertThat(retrospectiveService.getActionItems(retrospective.getId(), "Alice", "HIGH", true))
                .containsExactly(high);
        assertThatThrownBy(() -> retrospectiveService.getActionItems(retrospective.getId(), "Alice", "INVALID", null))
                .isInstanceOf(RetroflowException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    void actionItemListsSortHighToLowAndTieBreakById() {
        Retrospective retrospective = retrospective(team());
        ActionItem low = retrospectiveService.addActionItem(retrospective.getId(), "Low", "LOW", "Alice");
        ActionItem high1 = retrospectiveService.addActionItem(retrospective.getId(), "High 1", "HIGH", "Alice");
        ActionItem medium = retrospectiveService.addActionItem(retrospective.getId(), "Medium", "MEDIUM", "Alice");
        ActionItem high2 = retrospectiveService.addActionItem(retrospective.getId(), "High 2", "HIGH", "Bob");
        assertThat(retrospectiveService.getActionItems(retrospective.getId(), "Alice", null, null))
                .containsExactly(high1, high2, medium, low);
    }

    @Test
    void apiUsesHttp200ForSuccessAndReturnsEntityOrAnErrorsArray() throws Exception {
        String teamName = "HTTP team " + token();
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", teamName, "members", List.of("Alice")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(teamName))
                .andExpect(jsonPath("$.id").isNumber());
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", " ", "members", List.of("Alice")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").isNotEmpty());
        mockMvc.perform(get("/api/v1/teams/{name}", "missing-" + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void missingResourcesReturnHttp404AndEmptyCollectionsReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/retrospectives/{title}", "missing-" + token()))
                .andExpect(status().isNotFound());
        Team team = team();
        mockMvc.perform(get("/api/v1/retrospectives/team/{name}", team.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidActionItemFilterIsHttp400AndNamesTheInvalidValue() throws Exception {
        Retrospective retrospective = retrospective(team());
        mockMvc.perform(get("/api/v1/retrospectives/{title}/action-items", retrospective.getTitle())
                        .param("memberName", "Alice")
                        .param("priority", "URGENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("URGENT")));
    }

    @Test
    void teamApiSupportsCaseInsensitiveLookupAndDeletedResourcesReturn404() throws Exception {
        String teamName = "API team " + token();
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", teamName, "members", List.of("Alice")))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teams/{name}", teamName.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(teamName));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/teams/{name}", teamName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teams/{name}", teamName))
                .andExpect(status().isNotFound());
    }

    @Test
    void retrospectiveApiRejectsInvalidStatusesAndDoesNotProvideReopening() throws Exception {
        Team team = team();
        String title = "Invalid status " + token();
        mockMvc.perform(post("/api/v1/retrospectives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("teamName", team.getName(), "title", title,
                                "date", "2026-04-03T00:00:00Z", "memberName", "Alice", "status", "UNKNOWN"))))
                .andExpect(status().isBadRequest());
        Retrospective retrospective = retrospective(team);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/retrospectives/{title}/reopen", retrospective.getTitle()))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedbackApiRejectsNonMembersAndAttemptsToChangeImmutableFields() throws Exception {
        Retrospective retrospective = retrospective(team());
        FeedbackItem item = retrospectiveService.addFeedbackItem(retrospective.getId(), "Existing", "WENT_WELL",
                "Alice");
        mockMvc.perform(put("/api/v1/feedback/{id}", item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "Changed", "memberName", "Unknown"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/feedback/{id}", item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "Changed", "memberName", "Bob", "submittedBy", "Bob"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/feedback/{id}", item.getId()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void actionItemApiRejectsCompletedCreationAndInvalidCompletedFilter() throws Exception {
        Retrospective retrospective = retrospective(team());
        mockMvc.perform(post("/api/v1/retrospectives/{title}/action-items", retrospective.getTitle())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "Already complete", "priority", "HIGH",
                                "submittedBy", "Alice", "completed", true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/retrospectives/{title}/action-items", retrospective.getTitle())
                        .param("memberName", "Alice").param("completed", "sometimes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("sometimes")));
    }

    private Team team() {
        return teamService.createTeam("Team " + token(), List.of("Alice", "Bob"));
    }

    private Retrospective retrospective(Team team) {
        return retrospectiveService.createRetrospective(team.getId(), "Retro " + token());
    }

    private Retrospective ensureRetro(Team team) {
        return retrospective(team);
    }

    private ActionItem actionItem(Retrospective retrospective) {
        return retrospectiveService.addActionItem(retrospective.getId(), "Action " + token(), "HIGH", "Alice");
    }

    private String token() {
        return UUID.randomUUID().toString();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
