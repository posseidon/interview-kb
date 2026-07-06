package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    Optional<Question> findByExternalId(String externalId);

    Optional<Question> findByContentHash(String contentHash);

    @Query("""
            SELECT DISTINCT q FROM Question q
            LEFT JOIN FETCH q.answers
            WHERE 1=1
            AND (:topicSlug IS NULL OR EXISTS (
                SELECT 1 FROM q.topics t WHERE t.slug = :topicSlug
            ))
            AND (:tagName IS NULL OR EXISTS (
                SELECT 1 FROM q.tags tg WHERE tg.name = :tagName
            ))
            AND (:keyword IS NULL OR LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Question> findFiltered(
            @Param("topicSlug") String topicSlug,
            @Param("tagName") String tagName,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT COUNT(q) FROM Question q JOIN q.topics t WHERE t.slug = :slug")
    long countByTopicSlug(@Param("slug") String slug);

    @Query("""
            SELECT q FROM Question q
            WHERE q.requiresImpl = false
            AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q)
            ORDER BY q.createdAt ASC
            """)
    List<Question> findUnansweredNonImpl();

    @Query("""
            SELECT q FROM Question q
            LEFT JOIN FETCH q.answers
            WHERE EXISTS (
                SELECT 1 FROM q.topics t WHERE t.slug = :topicSlug
            )
            ORDER BY q.frequency DESC, q.createdAt DESC
            """)
    List<Question> findByTopicSlug(@Param("topicSlug") String topicSlug, Pageable pageable);
}
