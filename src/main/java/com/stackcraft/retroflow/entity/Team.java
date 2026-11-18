package com.stackcraft.retroflow.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
    name = "team",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_team_name",
        columnNames = "name"
    )
)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotEmpty
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "team_member",
        joinColumns = @JoinColumn(name = "team_id", nullable = false),
        uniqueConstraints = @UniqueConstraint(
            name = "uk_team_member_name",
            columnNames = "member_name"
        )
    )
    @Column(name = "member_name", nullable = false, length = 100)
    private Set<@NotBlank @Size(max = 100) String> members =
        new LinkedHashSet<>();

    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    @OrderBy("date DESC, id DESC")
    private List<Retrospective> retrospectives = new ArrayList<>();

    public Team() {
    }

    public Team(Long id, String name, Set<String> members,
                List<Retrospective> retrospectives) {
        this.id = id;
        this.name = name;
        setMembers(members);
        setRetrospectives(retrospectives);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void setMembers(Set<String> members) {
        this.members = members == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(members);
    }

    public List<Retrospective> getRetrospectives() {
        return retrospectives;
    }

    public void setRetrospectives(List<Retrospective> retrospectives) {
        this.retrospectives = retrospectives == null
            ? new ArrayList<>()
            : new ArrayList<>(retrospectives);
    }
}
