package io.github.posseidon.knowledgebase.it.interview.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.basket.Basket;
import org.junit.jupiter.api.Test;

class GlobalModelAttributesTest {

  @Test
  void exposesBasketSize() {
    Basket basket = mock(Basket.class);
    when(basket.size()).thenReturn(3);
    GlobalModelAttributes attributes = new GlobalModelAttributes(basket);

    assertThat(attributes.basketSize()).isEqualTo(3);
  }
}
