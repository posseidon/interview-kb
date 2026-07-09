package io.github.posseidon.knowledgebase.it.interview.domain.skill;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String path;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "position_count")
    private Integer positionCount;

    @Column(name = "novice_criteria", columnDefinition = "TEXT")
    private String noviceCriteria;

    @Column(name = "intermediate_criteria", columnDefinition = "TEXT")
    private String intermediateCriteria;

    @Column(name = "advanced_criteria", columnDefinition = "TEXT")
    private String advancedCriteria;

    @Column(name = "expert_criteria", columnDefinition = "TEXT")
    private String expertCriteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Skill parent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Skill() {}

    public Skill(String name, String path, String description, Integer positionCount, Skill parent) {
        this.name = name;
        this.path = path;
        this.description = description;
        this.positionCount = positionCount;
        this.parent = parent;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPositionCount() {
        return positionCount;
    }

    public void setPositionCount(Integer positionCount) {
        this.positionCount = positionCount;
    }

    public String getNoviceCriteria() {
        return noviceCriteria;
    }

    public void setNoviceCriteria(String noviceCriteria) {
        this.noviceCriteria = noviceCriteria;
    }

    public String getIntermediateCriteria() {
        return intermediateCriteria;
    }

    public void setIntermediateCriteria(String intermediateCriteria) {
        this.intermediateCriteria = intermediateCriteria;
    }

    public String getAdvancedCriteria() {
        return advancedCriteria;
    }

    public void setAdvancedCriteria(String advancedCriteria) {
        this.advancedCriteria = advancedCriteria;
    }

    public String getExpertCriteria() {
        return expertCriteria;
    }

    public void setExpertCriteria(String expertCriteria) {
        this.expertCriteria = expertCriteria;
    }

    public Skill getParent() {
        return parent;
    }

    public void setParent(Skill parent) {
        this.parent = parent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
