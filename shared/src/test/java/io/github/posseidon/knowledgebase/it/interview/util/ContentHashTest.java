package io.github.posseidon.knowledgebase.it.interview.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentHashTest {

  @Test
  void sameContentProducesSameHash() {
    String a = ContentHash.sha256("What is polymorphism?");
    String b = ContentHash.sha256("What is polymorphism?");

    assertThat(a).isEqualTo(b);
  }

  @Test
  void differentContentProducesDifferentHash() {
    String a = ContentHash.sha256("What is polymorphism?");
    String b = ContentHash.sha256("What is inheritance?");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void normalizesWhitespaceBeforeHashing() {
    String a = ContentHash.sha256("  What   is\n polymorphism?  ");
    String b = ContentHash.sha256("What is polymorphism?");

    assertThat(a).isEqualTo(b);
  }

  @Test
  void producesLowercaseHexString() {
    String hash = ContentHash.sha256("content");

    assertThat(hash).matches("[0-9a-f]{64}");
  }
}
