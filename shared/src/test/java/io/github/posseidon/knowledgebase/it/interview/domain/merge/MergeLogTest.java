package io.github.posseidon.knowledgebase.it.interview.domain.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MergeLogTest {

  @Test
  void allArgConstructorSetsFields() {
    UUID targetId = UUID.randomUUID();

    MergeLog log = new MergeLog(targetId, "{}");

    assertThat(log.getIntoQuestionId()).isEqualTo(targetId);
    assertThat(log.getSourceSnapshot()).isEqualTo("{}");
  }

  @Test
  void gettersAndSettersRoundTrip() {
    MergeLog log = new MergeLog();
    UUID id = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant now = Instant.now();

    log.setId(id);
    log.setIntoQuestionId(targetId);
    log.setSourceSnapshot("{\"a\":1}");
    log.setSimilarity(0.95f);
    log.setNote("note");
    log.setMergedAt(now);

    assertThat(log.getId()).isEqualTo(id);
    assertThat(log.getIntoQuestionId()).isEqualTo(targetId);
    assertThat(log.getSourceSnapshot()).isEqualTo("{\"a\":1}");
    assertThat(log.getSimilarity()).isEqualTo(0.95f);
    assertThat(log.getNote()).isEqualTo("note");
    assertThat(log.getMergedAt()).isEqualTo(now);
  }
}
