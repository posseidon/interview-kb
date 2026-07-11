package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillResolverTest {

  private SkillRepository skillRepository;
  private SkillResolver resolver;

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    resolver = new SkillResolver(skillRepository);
  }

  @Test
  void returnsEmptyMapForEmptyInput() {
    assertThat(resolver.resolve(Set.of())).isEmpty();
  }

  @Test
  void resolvesSkillsCaseInsensitively() {
    Skill java = new Skill("Java", "Java", null, null, null);
    when(skillRepository.findByNameIgnoreCaseIn(anyCollection())).thenReturn(List.of(java));

    Map<String, Skill> resolved = resolver.resolve(Set.of("java"));

    assertThat(resolved).containsEntry("java", java);
  }

  @Test
  void omitsNamesWithNoMatch() {
    when(skillRepository.findByNameIgnoreCaseIn(anyCollection())).thenReturn(List.of());

    Map<String, Skill> resolved = resolver.resolve(Set.of("Unknown Skill"));

    assertThat(resolved).isEmpty();
  }
}
