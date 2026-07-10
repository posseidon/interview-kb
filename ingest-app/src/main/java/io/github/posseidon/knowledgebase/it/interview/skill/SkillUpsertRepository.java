package io.github.posseidon.knowledgebase.it.interview.skill;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Upserts one {@link SkillRow} by its path, matching the existing skill tree instead of duplicating
 * it.
 */
@Repository
class SkillUpsertRepository {

  private static final String UPSERT_SQL = """
      INSERT INTO skill (name, path, description, position_count, parent_id,
                          novice_criteria, intermediate_criteria, advanced_criteria, expert_criteria)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (path) DO UPDATE SET
          name = EXCLUDED.name,
          description = EXCLUDED.description,
          position_count = EXCLUDED.position_count,
          parent_id = EXCLUDED.parent_id,
          novice_criteria = EXCLUDED.novice_criteria,
          intermediate_criteria = EXCLUDED.intermediate_criteria,
          advanced_criteria = EXCLUDED.advanced_criteria,
          expert_criteria = EXCLUDED.expert_criteria
      RETURNING id
      """;

  private final JdbcTemplate jdbcTemplate;

  SkillUpsertRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  UUID upsert(SkillRow row, UUID parentId) {
    return jdbcTemplate.queryForObject(UPSERT_SQL, UUID.class,
        row.name(), row.path(), row.description(), row.positionCount(), parentId,
        row.novice(), row.intermediate(), row.advanced(), row.expert());
  }
}
