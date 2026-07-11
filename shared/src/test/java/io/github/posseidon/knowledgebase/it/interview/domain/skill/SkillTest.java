package io.github.posseidon.knowledgebase.it.interview.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillTest {

  @Test
  void allArgConstructorSetsFields() {
    Skill parent = new Skill();

    Skill skill = new Skill("Java", "Java", "description", 5, parent);

    assertThat(skill.getName()).isEqualTo("Java");
    assertThat(skill.getPath()).isEqualTo("Java");
    assertThat(skill.getDescription()).isEqualTo("description");
    assertThat(skill.getPositionCount()).isEqualTo(5);
    assertThat(skill.getParent()).isSameAs(parent);
  }

  @Test
  void gettersAndSettersRoundTrip() {
    Skill skill = new Skill();
    UUID id = UUID.randomUUID();
    Skill parent = new Skill();
    Instant now = Instant.now();

    skill.setId(id);
    skill.setName("Kafka");
    skill.setPath("Backend -> Kafka");
    skill.setDescription("desc");
    skill.setPositionCount(3);
    skill.setNoviceCriteria("novice");
    skill.setIntermediateCriteria("intermediate");
    skill.setAdvancedCriteria("advanced");
    skill.setExpertCriteria("expert");
    skill.setParent(parent);
    skill.setCreatedAt(now);

    assertThat(skill.getId()).isEqualTo(id);
    assertThat(skill.getName()).isEqualTo("Kafka");
    assertThat(skill.getPath()).isEqualTo("Backend -> Kafka");
    assertThat(skill.getDescription()).isEqualTo("desc");
    assertThat(skill.getPositionCount()).isEqualTo(3);
    assertThat(skill.getNoviceCriteria()).isEqualTo("novice");
    assertThat(skill.getIntermediateCriteria()).isEqualTo("intermediate");
    assertThat(skill.getAdvancedCriteria()).isEqualTo("advanced");
    assertThat(skill.getExpertCriteria()).isEqualTo("expert");
    assertThat(skill.getParent()).isSameAs(parent);
    assertThat(skill.getCreatedAt()).isEqualTo(now);
  }
}
