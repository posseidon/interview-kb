package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
