package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Optional<Topic> findBySlug(String slug);
}
