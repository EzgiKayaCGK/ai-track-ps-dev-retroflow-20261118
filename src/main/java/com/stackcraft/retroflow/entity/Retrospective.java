package com.stackcraft.retroflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "retrospective",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_retrospective_team_title",
        columnNames = {"team_id", "title"}
    )
)
public class Retrospective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(name = "retro_date", nullable = false)
    private LocalDate date;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RetrospectiveStatus status = RetrospectiveStatus.OPEN;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @OneToMany(mappedBy = "retrospective", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<FeedbackItem> feedbackItems = new ArrayList<>();

    public Retrospective() {
    }

    public Retrospective(Long id, String title, LocalDate date,
                         RetrospectiveStatus status, Team team,
                         List<FeedbackItem> feedbackItems) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.status = status;
        this.team = team;
        setFeedbackItems(feedbackItems);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public RetrospectiveStatus getStatus() {
        return status;
    }

    public void setStatus(RetrospectiveStatus status) {
        this.status = status;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public List<FeedbackItem> getFeedbackItems() {
        return feedbackItems;
    }

    public void setFeedbackItems(List<FeedbackItem> feedbackItems) {
        this.feedbackItems = feedbackItems == null
            ? new ArrayList<>()
            : new ArrayList<>(feedbackItems);
    }
}
