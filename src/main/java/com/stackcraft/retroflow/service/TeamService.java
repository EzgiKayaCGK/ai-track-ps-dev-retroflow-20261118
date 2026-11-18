package com.stackcraft.retroflow.service;

import com.stackcraft.retroflow.entity.Team;
import com.stackcraft.retroflow.exception.RetroflowException;
import com.stackcraft.retroflow.exception.ResourceNotFoundException;
import com.stackcraft.retroflow.repository.ActionItemRepository;
import com.stackcraft.retroflow.repository.FeedbackItemRepository;
import com.stackcraft.retroflow.repository.RetrospectiveRepository;
import com.stackcraft.retroflow.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final FeedbackItemRepository feedbackItemRepository;
    private final ActionItemRepository actionItemRepository;

    public TeamService(TeamRepository teamRepository, RetrospectiveRepository retrospectiveRepository,
                       FeedbackItemRepository feedbackItemRepository, ActionItemRepository actionItemRepository) {
        this.teamRepository = teamRepository;
        this.retrospectiveRepository = retrospectiveRepository;
        this.feedbackItemRepository = feedbackItemRepository;
        this.actionItemRepository = actionItemRepository;
    }

    @Transactional
    public Team createTeam(String name, List<String> members) {
        String normalizedName = validateName(name, "Team name");
        if (teamRepository.existsByNameIgnoreCaseAndDeletedFalse(normalizedName)) {
            throw new RetroflowException("A team with that name already exists.");
        }
        if (members == null || members.isEmpty()) {
            throw new RetroflowException("A team must have at least one member.");
        }

        Set<String> normalizedMembers = new HashSet<>();
        for (String member : members) {
            String validMember = validateMemberName(member);
            if (!normalizedMembers.add(validMember.toLowerCase(Locale.ROOT))) {
                throw new RetroflowException("Team member names must be unique.");
            }
        }

        Team team = new Team();
        team.setName(normalizedName);
        List<String> validatedMembers = members.stream().map(this::validateMemberName).toList();
        team.setMembers(new ArrayList<>(validatedMembers));
        return teamRepository.save(team);
    }

    @Transactional(readOnly = true)
    public List<Team> getTeams() {
        return teamRepository.findAllByDeletedFalseOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Team getTeam(String name) {
        return findTeam(name);
    }

    @Transactional
    public Team deleteTeam(String name) {
        Team team = findTeam(name);
        team.setDeleted(true);
        retrospectiveRepository.findAllByTeamIdAndDeletedFalseOrderByDateDescIdAsc(team.getId())
                .forEach(retrospective -> {
                    retrospective.setDeleted(true);
                    feedbackItemRepository.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(retrospective.getId())
                            .forEach(item -> item.setDeleted(true));
                    actionItemRepository.findAllByRetrospectiveIdAndDeletedFalseOrderByIdAsc(retrospective.getId())
                            .forEach(item -> item.setDeleted(true));
                });
        return teamRepository.save(team);
    }

    @Transactional
    public Team addMember(String teamName, String memberName) {
        Team team = findTeam(teamName);
        String member = validateMemberName(memberName);
        ensureMemberIsUnique(team, member, null);
        team.getMembers().add(member);
        return teamRepository.save(team);
    }

    @Transactional
    public Team editMember(String teamName, String memberName, String replacementName) {
        Team team = findTeam(teamName);
        int index = findMemberIndex(team, memberName);
        String replacement = validateMemberName(replacementName);
        ensureMemberIsUnique(team, replacement, index);
        team.getMembers().set(index, replacement);
        return teamRepository.save(team);
    }

    @Transactional
    public Team removeMember(String teamName, String memberName) {
        Team team = findTeam(teamName);
        int index = findMemberIndex(team, memberName);
        if (team.getMembers().size() == 1) {
            throw new RetroflowException("A team must have at least one member.");
        }
        team.getMembers().remove(index);
        return teamRepository.save(team);
    }

    public int findMemberIndex(Team team, String memberName) {
        if (memberName != null) {
            for (int i = 0; i < team.getMembers().size(); i++) {
                if (team.getMembers().get(i).equalsIgnoreCase(memberName)) {
                    return i;
                }
            }
        }
        throw new ResourceNotFoundException("Team member not found.");
    }

    private void ensureMemberIsUnique(Team team, String member, Integer ignoredIndex) {
        for (int i = 0; i < team.getMembers().size(); i++) {
            if (!Integer.valueOf(i).equals(ignoredIndex) && team.getMembers().get(i).equalsIgnoreCase(member)) {
                throw new RetroflowException("Team member names must be unique.");
            }
        }
    }

    private String validateMemberName(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || characterCount(value) > 256) {
            throw new RetroflowException("Member name must be non-empty, at most 256 characters, and have no surrounding whitespace.");
        }
        return value;
    }

    private Team findTeam(String name) {
        if (name == null) {
            throw new ResourceNotFoundException("Team not found.");
        }
        return teamRepository.findByNameIgnoreCaseAndDeletedFalse(name)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));
    }

    private String validateName(String value, String label) {
        if (value == null) {
            throw new RetroflowException(label + " is required.");
        }
        String validated = value.strip();
        if (validated.isBlank() || characterCount(validated) > 256) {
            throw new RetroflowException(label + " must be non-empty and at most 256 characters");
        }
        return validated;
    }

    private int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
