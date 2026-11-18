package com.stackcraft.retroflow.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RetrospectiveService {

    private final TeamRepository teamRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final FeedbackItemRepository feedbackItemRepository;
    private final ActionItemRepository actionItemRepository;

    public RetrospectiveService(TeamRepository teamRepository,
                                RetrospectiveRepository retrospectiveRepository,
                                FeedbackItemRepository feedbackItemRepository,
                                ActionItemRepository actionItemRepository) {
        this.teamRepository = teamRepository;
        this.retrospectiveRepository = retrospectiveRepository;
        this.feedbackItemRepository = feedbackItemRepository;
        this.actionItemRepository = actionItemRepository;
    }

    @Transactional
    public Retrospective createRetrospective(Long teamId, String title) {
        Team team = findTeam(teamId);
        if (team.getMembers().isEmpty()) {
            throw new RetroflowException("The team has no current members.");
        }
        return createRetrospective(teamId, title, team.getMembers().get(0),
                OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    @Transactional
    public Retrospective createRetrospective(Long teamId, String title, String memberName, String date) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));
        validateMember(team, memberName);
        String validTitle = validateTitle(title);
        var validDate = parseUtcDate(date);
        if (retrospectiveRepository.existsByTitleIgnoreCaseAndDeletedFalse(validTitle)) {
            throw new RetroflowException("A retrospective with that title already exists.");
        }
        if (retrospectiveRepository.existsByTeamIdAndStatusAndDeletedFalse(teamId, RetrospectiveStatus.OPEN)) {
            throw new RetroflowException("The team already has an open retrospective.");
        }

        Retrospective retrospective = new Retrospective();
        retrospective.setTeam(team);
        retrospective.setTitle(validTitle);
        retrospective.setDate(validDate);
        retrospective.setStatus(RetrospectiveStatus.OPEN);
        return retrospectiveRepository.save(retrospective);
    }

    @Transactional(readOnly = true)
    public List<Retrospective> getRetrospectives() {
        return retrospectiveRepository.findAllByDeletedFalseOrderByDateDescIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Retrospective> getRetrospectivesForTeam(String teamName) {
        Team team = findTeam(teamName);
        return retrospectiveRepository.findAllByTeamIdAndDeletedFalseOrderByDateDescIdAsc(team.getId());
    }

    @Transactional(readOnly = true)
    public Retrospective getOpenRetrospective(String teamName) {
        Team team = findTeam(teamName);
        return retrospectiveRepository.findByTeamIdAndStatusAndDeletedFalse(team.getId(), RetrospectiveStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Open retrospective not found."));
    }

    @Transactional(readOnly = true)
    public Retrospective getRetrospective(String title) {
        return retrospectiveRepository.findByTitleIgnoreCaseAndDeletedFalse(title)
                .orElseThrow(() -> new ResourceNotFoundException("Retrospective not found."));
    }

    @Transactional
    public Retrospective closeRetrospective(Long retrospectiveId) {
        Retrospective retrospective = findRetrospective(retrospectiveId);
        if (retrospective.getTeam().getMembers().isEmpty()) {
            throw new RetroflowException("The team has no current members.");
        }
        return closeRetrospective(retrospectiveId, retrospective.getTeam().getMembers().get(0));
    }

    @Transactional
    public Retrospective closeRetrospective(Long retrospectiveId, String memberName) {
        Retrospective retrospective = findRetrospective(retrospectiveId);
        validateMember(retrospective.getTeam(), memberName);
        if (retrospective.getStatus() != RetrospectiveStatus.OPEN) {
            throw new RetroflowException("The retrospective is already closed.");
        }
        retrospective.setStatus(RetrospectiveStatus.CLOSED);
        return retrospectiveRepository.save(retrospective);
    }

    @Transactional
    public Retrospective closeRetrospective(String title, String memberName) {
        return closeRetrospective(getRetrospective(title).getId(), memberName);
    }

    @Transactional
    public FeedbackItem addFeedbackItem(Long retrospectiveId, String content, String type, String submittedBy) {
        Retrospective retrospective = findRetrospective(retrospectiveId);
        ensureOpen(retrospective);
        validateMember(retrospective.getTeam(), submittedBy);
        FeedbackItem feedbackItem = new FeedbackItem();
        feedbackItem.setRetrospective(retrospective);
        feedbackItem.setContent(validateContent(content, "Feedback content"));
        feedbackItem.setType(parseFeedbackType(type));
        feedbackItem.setSubmittedBy(submittedBy);
        return feedbackItemRepository.save(feedbackItem);
    }

    @Transactional
    public FeedbackItem modifyFeedbackItem(Long feedbackItemId, String content, String type, String memberName) {
        FeedbackItem item = findFeedbackItem(feedbackItemId);
        ensureOpen(item.getRetrospective());
        validateMember(item.getRetrospective().getTeam(), memberName);
        if (content != null) {
            item.setContent(validateContent(content, "Feedback content"));
        }
        if (type != null) {
            item.setType(parseFeedbackType(type));
        }
        return feedbackItemRepository.save(item);
    }

    @Transactional
    public FeedbackItem deleteFeedbackItem(Long feedbackItemId, String memberName) {
        FeedbackItem item = findFeedbackItem(feedbackItemId);
        ensureOpen(item.getRetrospective());
        validateMember(item.getRetrospective().getTeam(), memberName);
        item.setDeleted(true);
        return feedbackItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<FeedbackItem> getFeedbackItems(Long retrospectiveId) {
        findRetrospective(retrospectiveId);
        return feedbackItemRepository.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(retrospectiveId)
                .stream()
                .filter(item -> item.getType() != FeedbackItemType.ACTION_ITEM)
                .toList();
    }

    @Transactional
    public ActionItem addActionItem(Long retrospectiveId, String content, String priority, String submittedBy) {
        return addActionItem(retrospectiveId, content, priority, submittedBy, false);
    }

    @Transactional
    public ActionItem addActionItem(Long retrospectiveId, String content, String priority, String submittedBy,
                                    boolean completed) {
        Retrospective retrospective = findRetrospective(retrospectiveId);
        ensureOpen(retrospective);
        validateMember(retrospective.getTeam(), submittedBy);
        if (completed) {
            throw new RetroflowException("A new action item cannot be completed.");
        }
        ActionItem actionItem = new ActionItem();
        actionItem.setRetrospective(retrospective);
        actionItem.setContent(validateContent(content, "Action-item content"));
        actionItem.setSubmittedBy(submittedBy);
        actionItem.setPriority(parsePriority(priority));
        actionItem.setCompleted(false);
        return actionItemRepository.save(actionItem);
    }

    @Transactional
    public ActionItem modifyActionItem(Long actionItemId, String content, String priority, Boolean completed,
                                       String memberName) {
        ActionItem item = findActionItem(actionItemId);
        ensureOpen(item.getRetrospective());
        validateMember(item.getRetrospective().getTeam(), memberName);
        if (content != null) {
            item.setContent(validateContent(content, "Action-item content"));
        }
        if (priority != null) {
            item.setPriority(parsePriority(priority));
        }
        if (completed != null) {
            if (completed && !item.isCompleted()) {
                item.setCompleted(true);
            } else if (!completed && item.isCompleted()) {
                throw new RetroflowException("A completed action item cannot be uncompleted.");
            }
        }
        return actionItemRepository.save(item);
    }

    @Transactional
    public ActionItem completeActionItem(Long actionItemId) {
        ActionItem item = findActionItem(actionItemId);
        if (item.getRetrospective().getTeam().getMembers().isEmpty()) {
            throw new RetroflowException("The team has no current members.");
        }
        return completeActionItem(actionItemId, item.getRetrospective().getTeam().getMembers().get(0));
    }

    @Transactional
    public ActionItem completeActionItem(Long actionItemId, String memberName) {
        ActionItem item = findActionItem(actionItemId);
        ensureOpen(item.getRetrospective());
        validateMember(item.getRetrospective().getTeam(), memberName);
        if (item.isCompleted()) {
            throw new RetroflowException("The action item is already completed.");
        }
        item.setCompleted(true);
        return actionItemRepository.save(item);
    }

    @Transactional
    public ActionItem uncompleteActionItem(Long actionItemId) {
        ActionItem item = findActionItem(actionItemId);
        ensureOpen(item.getRetrospective());
        if (item.isCompleted()) {
            throw new RetroflowException("A completed action item cannot be uncompleted.");
        }
        throw new RetroflowException("The action item is not completed.");
    }

    @Transactional
    public ActionItem deleteActionItem(Long actionItemId, String memberName) {
        ActionItem item = findActionItem(actionItemId);
        ensureOpen(item.getRetrospective());
        validateMember(item.getRetrospective().getTeam(), memberName);
        item.setDeleted(true);
        return actionItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<ActionItem> getActionItems(Long retrospectiveId, String memberName, String priority,
                                           Boolean completed) {
        Retrospective retrospective = findRetrospective(retrospectiveId);
        validateMember(retrospective.getTeam(), memberName);
        ActionItemPriority requestedPriority = priority == null ? null : parsePriority(priority);
        return actionItemRepository.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(retrospectiveId)
                .stream()
                .filter(item -> requestedPriority == null || item.getPriority() == requestedPriority)
                .filter(item -> completed == null || item.isCompleted() == completed)
                .sorted(Comparator.comparing(ActionItem::getPriority).reversed().thenComparing(ActionItem::getId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActionItem getActionItem(Long actionItemId, String memberName) {
        ActionItem item = findActionItem(actionItemId);
        validateMember(item.getRetrospective().getTeam(), memberName);
        return item;
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .filter(team -> !team.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));
    }

    private Team findTeam(String teamName) {
        return teamRepository.findByNameIgnoreCaseAndDeletedFalse(teamName)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));
    }

    private Retrospective findRetrospective(Long retrospectiveId) {
        Retrospective retrospective = retrospectiveRepository.findByIdAndDeletedFalse(retrospectiveId)
                .orElseThrow(() -> new ResourceNotFoundException("Retrospective not found."));
        if (retrospective.getTeam().isDeleted()) {
            throw new ResourceNotFoundException("Retrospective not found.");
        }
        return retrospective;
    }

    private FeedbackItem findFeedbackItem(Long feedbackItemId) {
        FeedbackItem item = feedbackItemRepository.findByIdAndDeletedFalse(feedbackItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback item not found."));
        findRetrospective(item.getRetrospective().getId());
        return item;
    }

    private ActionItem findActionItem(Long actionItemId) {
        ActionItem item = actionItemRepository.findByIdAndDeletedFalse(actionItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found."));
        findRetrospective(item.getRetrospective().getId());
        return item;
    }

    private void validateMember(Team team, String memberName) {
        if (memberName == null
                || team.getMembers().stream().noneMatch(member -> member.equalsIgnoreCase(memberName))) {
            throw new RetroflowException("The supplied member is not a current member of the team.");
        }
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank() || characterCount(title) > 256) {
            throw new RetroflowException("Retrospective title must be non-empty and at most 256 characters.");
        }
        return title;
    }

    private String validateContent(String value, String label) {
        if (value == null || value.isBlank() || characterCount(value) > 256 || !value.equals(value.strip())) {
            throw new RetroflowException(label
                    + " must be non-empty, at most 256 characters, and have no surrounding whitespace.");
        }
        return value;
    }

    private FeedbackItemType parseFeedbackType(String type) {
        try {
            FeedbackItemType value = FeedbackItemType.valueOf(type.toUpperCase(Locale.ROOT));
            if (value == FeedbackItemType.ACTION_ITEM) {
                throw new RetroflowException("Create action items using the action-item operation.");
            }
            return value;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new RetroflowException("Feedback type must be WENT_WELL or NEEDS_IMPROVEMENT.");
        }
    }

    private ActionItemPriority parsePriority(String priority) {
        try {
            return ActionItemPriority.valueOf(priority.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new RetroflowException("Action-item priority must be LOW, MEDIUM, or HIGH: " + priority);
        }
    }

    private java.time.LocalDate parseUtcDate(String value) {
        if (value == null || value.isBlank()) {
            throw new RetroflowException("Retrospective date is required in UTC ISO-8601 format.");
        }
        try {
            OffsetDateTime dateTime = OffsetDateTime.parse(value);
            if (!dateTime.getOffset().equals(ZoneOffset.UTC)) {
                throw new RetroflowException("Retrospective date must use the UTC offset.");
            }
            return dateTime.toLocalDate();
        } catch (DateTimeParseException exception) {
            throw new RetroflowException("Retrospective date must be a valid UTC ISO-8601 date-time.");
        }
    }

    private int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private void ensureOpen(Retrospective retrospective) {
        if (retrospective.getStatus() != RetrospectiveStatus.OPEN) {
            throw new RetroflowException("The retrospective is closed and cannot be modified.");
        }
    }
}
