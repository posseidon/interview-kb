package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    Optional<Question> findByExternalId(String externalId);

    List<Question> findAllByExternalIdIn(Collection<String> externalIds);

    Optional<Question> findByContentHash(String contentHash);
    List<Question> findAllByContentHashIn(Collection<String> hashes);

    default Map<String, Question> indexByExternalId(Collection<String> externalIds) {
        return findAllByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(Question::getExternalId, q -> q));
    }

    default Map<String, Question> indexByContentHash(Collection<String> contentHashValues) {
        return findAllByContentHashIn(contentHashValues).stream()
                .collect(Collectors.toMap(Question::getContentHash, q -> q));
    }

    long countByRequiresImpl(boolean requiresImpl);

    @Query("SELECT q.id FROM Question q WHERE q.id IN :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT q FROM Question q
            WHERE q.requiresImpl = false
            AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q)
            ORDER BY q.createdAt ASC
            """)
    List<Question> findUnansweredNonImpl();

    @Query("""
            SELECT DISTINCT q FROM Question q
            LEFT JOIN FETCH q.answers
            WHERE 1=1
            AND (:skillId IS NULL OR EXISTS (
                SELECT 1 FROM q.skills s WHERE s.id = :skillId
            ))
            AND (:keyword IS NULL OR LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Question> findFilteredBySkill(
            @Param("skillId") UUID skillId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT q FROM Question q
            WHERE EXISTS (
                SELECT 1 FROM q.skills s WHERE s.id = :skillId
            )
            ORDER BY q.frequency DESC, q.createdAt DESC
            """)
    List<Question> findBySkillId(@Param("skillId") UUID skillId, Pageable pageable);

    @Query(value = """
            SELECT q.* FROM question q
            JOIN question_skill qs ON qs.question_id = q.id
            WHERE qs.skill_id = :skillId
            AND (q.level = :level OR q.level IS NULL)
            ORDER BY q.frequency DESC, q.created_at DESC
            """, nativeQuery = true)
    List<Question> findBySkillIdAndLevel(@Param("skillId") UUID skillId, @Param("level") String level);
}
