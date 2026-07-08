package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Optional<Topic> findBySlug(String slug);
    List<Topic> findAllBySlugIn(Collection<String> slugs);

    @Query("""
            SELECT t FROM Topic t
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY t.name
            """)
    List<Topic> search(@Param("q") String q);
}
