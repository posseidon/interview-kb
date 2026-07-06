package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {
}
