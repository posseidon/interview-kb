package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SkillUpsertRepositoryTest {

  @Test
  void upsertPassesRowFieldsAndParentIdToJdbcTemplate() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    SkillUpsertRepository repository = new SkillUpsertRepository(jdbcTemplate);
    UUID parentId = UUID.randomUUID();
    UUID generatedId = UUID.randomUUID();
    SkillRow row = new SkillRow("Java", "Backend -> Java", "desc", 3, "n", "i", "a", "e");

    when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class),
        eq("Java"), eq("Backend -> Java"), eq("desc"), eq(3), eq(parentId),
        eq("n"), eq("i"), eq("a"), eq("e")))
        .thenReturn(generatedId);

    UUID result = repository.upsert(row, parentId);

    assertThat(result).isEqualTo(generatedId);
  }
}
