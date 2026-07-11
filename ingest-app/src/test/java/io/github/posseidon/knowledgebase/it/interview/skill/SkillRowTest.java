package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillRowTest {

  private static SkillRow row(String path) {
    return new SkillRow("name", path, null, null, null, null, null, null);
  }

  @Test
  void depthIsZeroForRootLevelPath() {
    assertThat(row("Backend").depth()).isZero();
  }

  @Test
  void depthCountsPathSeparators() {
    assertThat(row("Backend -> Java").depth()).isEqualTo(1);
    assertThat(row("Backend -> Java -> Streams").depth()).isEqualTo(2);
  }

  @Test
  void parentPathIsNullForRootLevelPath() {
    assertThat(row("Backend").parentPath()).isNull();
  }

  @Test
  void parentPathIsEverythingBeforeLastSeparator() {
    assertThat(row("Backend -> Java -> Streams").parentPath()).isEqualTo("Backend -> Java");
  }
}
