package io.github.posseidon.knowledgebase.it.interview.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DecisionTest {

  @Test
  void hasThreeOutcomes() {
    assertThat(Decision.values()).containsExactly(
        Decision.NO_HIRE, Decision.MAYBE, Decision.GOOD_CANDIDATE);
  }

  @Test
  void valueOfParsesEnumName() {
    assertThat(Decision.valueOf("GOOD_CANDIDATE")).isEqualTo(Decision.GOOD_CANDIDATE);
  }
}
