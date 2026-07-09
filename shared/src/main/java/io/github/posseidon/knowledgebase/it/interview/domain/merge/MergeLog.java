package io.github.posseidon.knowledgebase.it.interview.domain.merge;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merge_log")
public class MergeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "into_question_id")
    private UUID intoQuestionId;

    @Column(name = "source_snapshot", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String sourceSnapshot;

    private Float similarity;

    private String note;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt = Instant.now();

    public MergeLog() {}

    public MergeLog(UUID intoQuestionId, String sourceSnapshot) {
        this.intoQuestionId = intoQuestionId;
        this.sourceSnapshot = sourceSnapshot;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIntoQuestionId() {
        return intoQuestionId;
    }

    public void setIntoQuestionId(UUID intoQuestionId) {
        this.intoQuestionId = intoQuestionId;
    }

    public String getSourceSnapshot() {
        return sourceSnapshot;
    }

    public void setSourceSnapshot(String sourceSnapshot) {
        this.sourceSnapshot = sourceSnapshot;
    }

    public Float getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Float similarity) {
        this.similarity = similarity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public void setMergedAt(Instant mergedAt) {
        this.mergedAt = mergedAt;
    }
}
