package com.stackcraft.retroflow;

import com.stackcraft.retroflow.entity.ActionItem;
import com.stackcraft.retroflow.entity.ActionItemPriority;
import com.stackcraft.retroflow.entity.FeedbackItem;
import com.stackcraft.retroflow.entity.FeedbackItemType;
import com.stackcraft.retroflow.entity.Retrospective;
import com.stackcraft.retroflow.entity.RetrospectiveStatus;
import com.stackcraft.retroflow.entity.Team;
import com.stackcraft.retroflow.exception.ResourceNotFoundException;
import com.stackcraft.retroflow.exception.RetroflowException;
import com.stackcraft.retroflow.repository.ActionItemRepository;
import com.stackcraft.retroflow.repository.FeedbackItemRepository;
import com.stackcraft.retroflow.repository.RetrospectiveRepository;
import com.stackcraft.retroflow.repository.TeamRepository;
import com.stackcraft.retroflow.service.RetrospectiveService;
import com.stackcraft.retroflow.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceMutationTest {

    private TeamRepository teams;
    private RetrospectiveRepository retrospectives;
    private FeedbackItemRepository feedback;
    private ActionItemRepository actions;
    private TeamService teamService;
    private RetrospectiveService retrospectiveService;
    private Team team;
    private Retrospective retrospective;

    @BeforeEach
    void setUp() {
        teams = mock(TeamRepository.class);
        retrospectives = mock(RetrospectiveRepository.class);
        feedback = mock(FeedbackItemRepository.class);
        actions = mock(ActionItemRepository.class);
        teamService = new TeamService(teams, retrospectives, feedback, actions);
        retrospectiveService = new RetrospectiveService(teams, retrospectives, feedback, actions);
        team = new Team();
        team.setId(1L);
        team.setName("Team");
        team.setMembers(new ArrayList<>(List.of("Alice", "Bob")));
        retrospective = new Retrospective();
        retrospective.setId(2L);
        retrospective.setTeam(team);
        retrospective.setStatus(RetrospectiveStatus.OPEN);
    }

    @Test
    void namesAcceptExactly256CodePointsAndRejectLongerOrNull() {
        when(teams.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(teamService.createTeam("T".repeat(256), List.of("M".repeat(256))).getMembers())
                .containsExactly("M".repeat(256));
        assertThatThrownBy(() -> teamService.createTeam(null, List.of("Alice")))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Team", java.util.Arrays.asList((String) null)))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.createTeam("Team", List.of("M".repeat(257))))
                .isInstanceOf(RetroflowException.class);
    }

    @Test
    void editingMemberAllowsSameMemberButRejectsAnotherMembersName() {
        when(teams.findByNameIgnoreCaseAndDeletedFalse("Team")).thenReturn(Optional.of(team));
        when(teams.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(teamService.editMember("Team", "ALICE", "alice").getMembers())
                .containsExactly("alice", "Bob");
        assertThatThrownBy(() -> teamService.editMember("Team", "alice", "BOB"))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.addMember("Team", "bob"))
                .isInstanceOf(RetroflowException.class);
        assertThatThrownBy(() -> teamService.findMemberIndex(team, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(teamService.findMemberIndex(team, "bob")).isEqualTo(1);
    }

    @Test
    void feedbackListExcludesActionItems() {
        FeedbackItem normal = new FeedbackItem();
        normal.setType(FeedbackItemType.WENT_WELL);
        ActionItem action = new ActionItem();
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(feedback.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(2L))
                .thenReturn(List.of(normal, action));
        assertThat(retrospectiveService.getFeedbackItems(2L)).containsExactly(normal);
    }

    @Test
    void actionItemFiltersIndependentlyMatchPriorityAndCompletion() {
        ActionItem highDone = action(1L, ActionItemPriority.HIGH, true);
        ActionItem highOpen = action(2L, ActionItemPriority.HIGH, false);
        ActionItem lowDone = action(3L, ActionItemPriority.LOW, true);
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(actions.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(2L))
                .thenReturn(List.of(lowDone, highOpen, highDone));
        assertThat(retrospectiveService.getActionItems(2L, "alice", "HIGH", true)).containsExactly(highDone);
        assertThat(retrospectiveService.getActionItems(2L, "Alice", "HIGH", null))
                .containsExactly(highDone, highOpen);
        assertThat(retrospectiveService.getActionItems(2L, "Alice", null, false)).containsExactly(highOpen);
    }

    @Test
    void partialActionUpdatesPreserveFieldsAndCannotUndoCompletion() {
        ActionItem item = action(3L, ActionItemPriority.LOW, false);
        item.setContent("Before");
        item.setRetrospective(retrospective);
        when(actions.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(item));
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(actions.save(any(ActionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ActionItem updated = retrospectiveService.modifyActionItem(3L, null, null, true, "Alice");
        assertThat(updated.getContent()).isEqualTo("Before");
        assertThat(updated.getPriority()).isEqualTo(ActionItemPriority.LOW);
        assertThat(updated.isCompleted()).isTrue();
        assertThat(retrospectiveService.modifyActionItem(3L, null, null, true, "Bob").isCompleted()).isTrue();
        assertThatThrownBy(() -> retrospectiveService.modifyActionItem(3L, null, null, false, "Bob"))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("cannot be uncompleted");
    }

    @Test
    void deletedParentMakesRetrospectiveUnavailable() {
        team.setDeleted(true);
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        assertThatThrownBy(() -> retrospectiveService.getFeedbackItems(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void memberOperationsReturnUpdatedTeamAndMissingMembersAreNotFound() {
        when(teams.findByNameIgnoreCaseAndDeletedFalse("Team")).thenReturn(Optional.of(team));
        when(teams.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(teamService.addMember("Team", "Carol")).isSameAs(team);
        assertThat(teamService.removeMember("Team", "Carol")).isSameAs(team);
        assertThatThrownBy(() -> teamService.findMemberIndex(team, "Missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> teamService.findMemberIndex(new Team(), "Missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void teamListingAndDeletionIncludeActionItems() {
        ActionItem item = action(3L, ActionItemPriority.HIGH, false);
        when(teams.findByNameIgnoreCaseAndDeletedFalse("Team")).thenReturn(Optional.of(team));
        when(teams.findAllByDeletedFalseOrderByIdAsc()).thenReturn(List.of(team));
        when(teams.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retrospectives.findAllByTeamIdAndDeletedFalseOrderByDateDescIdAsc(1L))
                .thenReturn(List.of(retrospective));
        when(actions.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(2L)).thenReturn(List.of(item));
        assertThat(teamService.getTeams()).containsExactly(team);
        teamService.deleteTeam("Team");
        assertThat(item.isDeleted()).isTrue();
    }

    @Test
    void retrospectiveCreationAcceptsMaximumTitleAndRejectsDeletedTeams() {
        when(teams.findByIdForUpdate(1L)).thenReturn(Optional.of(team));
        when(retrospectives.save(any(Retrospective.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(retrospectiveService.createRetrospective(1L, "T".repeat(256), "Alice",
                "2026-04-03T00:00:00Z").getTitle()).hasSize(256);
        team.setDeleted(true);
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(1L, "Title", "Alice",
                "2026-04-03T00:00:00Z")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void convenienceCreationRejectsDeletedTeams() {
        team.setDeleted(true);
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        assertThatThrownBy(() -> retrospectiveService.createRetrospective(1L, "Title"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(teams, never()).findByIdForUpdate(any());
    }

    @Test
    void closingRequiresMembershipAndConvenienceMethodReturnsRetrospective() {
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(retrospectives.save(any(Retrospective.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThatThrownBy(() -> retrospectiveService.closeRetrospective(2L, "Missing"))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("not a current member");
        assertThat(retrospectiveService.closeRetrospective(2L)).isSameAs(retrospective);
    }

    @Test
    void feedbackDeletionRequiresMembership() {
        FeedbackItem item = new FeedbackItem();
        item.setRetrospective(retrospective);
        when(feedback.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(item));
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        assertThatThrownBy(() -> retrospectiveService.deleteFeedbackItem(3L, "Missing"))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("not a current member");
    }

    @Test
    void missingFeedbackAndTeamLookupsReportNotFound() {
        assertThatThrownBy(() -> retrospectiveService.deleteFeedbackItem(3L, "Alice"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Feedback item not found");
        assertThatThrownBy(() -> retrospectiveService.getRetrospectivesForTeam("Missing"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Team not found");
    }

    @Test
    void missingOpenRetrospectiveReportsNotFoundAndClosingByTitleReturnsResult() {
        when(teams.findByNameIgnoreCaseAndDeletedFalse("Team")).thenReturn(Optional.of(team));
        assertThatThrownBy(() -> retrospectiveService.getOpenRetrospective("Team"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Open retrospective not found");
        when(retrospectives.findByTitleIgnoreCaseAndDeletedFalse("Retro")).thenReturn(Optional.of(retrospective));
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(retrospectives.save(any(Retrospective.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(retrospectiveService.closeRetrospective("Retro", "Alice")).isSameAs(retrospective);
    }

    @Test
    void actionMutationsRequireMembershipAndConvenienceCompletionReturnsItem() {
        ActionItem item = action(3L, ActionItemPriority.HIGH, false);
        item.setRetrospective(retrospective);
        when(actions.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(item));
        when(retrospectives.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(retrospective));
        when(actions.save(any(ActionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThatThrownBy(() -> retrospectiveService.modifyActionItem(3L, null, null, null, "Missing"))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("not a current member");
        assertThatThrownBy(() -> retrospectiveService.deleteActionItem(3L, "Missing"))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("not a current member");
        assertThat(retrospectiveService.completeActionItem(3L)).isSameAs(item);
        assertThatThrownBy(() -> retrospectiveService.uncompleteActionItem(3L))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("cannot be uncompleted");
        item.setCompleted(false);
        assertThatThrownBy(() -> retrospectiveService.uncompleteActionItem(3L))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("not completed");
        retrospective.setStatus(RetrospectiveStatus.CLOSED);
        assertThatThrownBy(() -> retrospectiveService.uncompleteActionItem(3L))
                .isInstanceOf(RetroflowException.class).hasMessageContaining("closed");
    }

    private ActionItem action(long id, ActionItemPriority priority, boolean completed) {
        ActionItem item = new ActionItem();
        item.setId(id);
        item.setPriority(priority);
        item.setCompleted(completed);
        return item;
    }
}
