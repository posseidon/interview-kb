package io.github.posseidon.knowledgebase.it.interview.basket;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BasketTest {

  @Test
  void startsEmpty() {
    Basket basket = new Basket();

    assertThat(basket.isEmpty()).isTrue();
    assertThat(basket.size()).isZero();
  }

  @Test
  void putAddsItem() {
    Basket basket = new Basket();
    UUID skillId = UUID.randomUUID();

    basket.put(skillId, SkillLevel.ADVANCED);

    assertThat(basket.isEmpty()).isFalse();
    assertThat(basket.size()).isEqualTo(1);
    assertThat(basket.items()).containsEntry(skillId, SkillLevel.ADVANCED);
  }

  @Test
  void removeDeletesItem() {
    Basket basket = new Basket();
    UUID skillId = UUID.randomUUID();
    basket.put(skillId, SkillLevel.NOVICE);

    basket.remove(skillId);

    assertThat(basket.isEmpty()).isTrue();
  }

  @Test
  void clearRemovesAllItems() {
    Basket basket = new Basket();
    basket.put(UUID.randomUUID(), SkillLevel.NOVICE);
    basket.put(UUID.randomUUID(), SkillLevel.EXPERT);

    basket.clear();

    assertThat(basket.isEmpty()).isTrue();
    assertThat(basket.size()).isZero();
  }
}
