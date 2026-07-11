package io.github.posseidon.knowledgebase.it.interview.dto.ingest.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionDtoTest {

  @Test
  void defaultsLevelToNoviceWhenNull() {
    QuestionDto dto = new QuestionDto("ext-1", "content", false, "java",
        List.of(), List.of(), null);

    assertThat(dto.level()).isEqualTo(SkillLevel.NOVICE);
  }

  @Test
  void keepsExplicitLevel() {
    QuestionDto dto = new QuestionDto("ext-1", "content", false, "java",
        List.of(), List.of(), SkillLevel.EXPERT);

    assertThat(dto.level()).isEqualTo(SkillLevel.EXPERT);
  }
}
