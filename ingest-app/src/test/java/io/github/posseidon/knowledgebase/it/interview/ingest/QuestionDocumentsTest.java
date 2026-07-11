package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class QuestionDocumentsTest {

  @Test
  void mapsQuestionToDocumentWithMatchingId() {
    Question question = new Question("What is polymorphism?", "hash");
    UUID id = UUID.randomUUID();
    question.setId(id);
    question.setFrequency(4);
    Skill skill = new Skill("OOP", "OOP", null, null, null);
    skill.setId(UUID.randomUUID());
    question.setSkills(Set.of(skill));

    Document doc = QuestionDocuments.toDocument(question);

    assertThat(doc.getId()).isEqualTo(id.toString());
    assertThat(doc.getText()).isEqualTo("What is polymorphism?");
    assertThat(doc.getMetadata()).containsEntry("frequency", 4);
    assertThat(doc.getMetadata().get("skills")).isEqualTo(List.of("OOP"));
  }
}
