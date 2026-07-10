package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    default Map<UUID, Set<String>> groupContentHashesByQuestionId(Collection<UUID> questionIds) {
        return findByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getQuestion().getId(),
                        Collectors.mapping(Answer::getContentHash, Collectors.toSet())));
    }

    @Query("SELECT a FROM Answer a WHERE a.question.id IN :ids")
    List<Answer> findByQuestionIds(@Param("ids") Collection<UUID> ids);
}
