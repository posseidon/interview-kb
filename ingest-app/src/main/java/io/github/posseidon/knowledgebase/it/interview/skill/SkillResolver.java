package io.github.posseidon.knowledgebase.it.interview.skill;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SkillResolver {

    private static final Logger log = LoggerFactory.getLogger(SkillResolver.class);

    private final SkillRepository skillRepository;

    public SkillResolver(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    // Skills are owned by SkillIngestService's xlsx import; this only links to ones that
    // already exist by exact case-insensitive name — it never creates new skill rows.
    public Map<String, Skill> resolve(Set<String> names) {
        if (names.isEmpty()) return Map.of();

        Set<String> lowerNames = names.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Map<String, Skill> byLowerName = new HashMap<>();
        for (Skill s : skillRepository.findByNameIgnoreCaseIn(lowerNames)) {
            byLowerName.putIfAbsent(s.getName().toLowerCase(), s);
        }

        Map<String, Skill> resolved = new HashMap<>();
        for (String name : names) {
            Skill match = byLowerName.get(name.toLowerCase());
            if (match != null) {
                resolved.put(name, match);
            } else {
                log.warn("No skill matches '{}' — question will not be linked to a skill", name);
            }
        }
        return resolved;
    }
}
