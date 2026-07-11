package io.github.posseidon.knowledgebase.it.interview.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillLevelTest {

  @Test
  void hasFourLevelsInAscendingOrder() {
    assertThat(SkillLevel.values()).containsExactly(
        SkillLevel.NOVICE, SkillLevel.INTERMEDIATE, SkillLevel.ADVANCED, SkillLevel.EXPERT);
  }

  @Test
  void valueOfParsesEnumName() {
    assertThat(SkillLevel.valueOf("ADVANCED")).isEqualTo(SkillLevel.ADVANCED);
  }
}
