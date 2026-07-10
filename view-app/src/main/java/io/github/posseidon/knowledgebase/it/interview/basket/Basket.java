package io.github.posseidon.knowledgebase.it.interview.basket;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@SessionScope
public class Basket {

    private final Map<UUID, SkillLevel> items = new LinkedHashMap<>();

    public void put(UUID skillId, SkillLevel level) {
        items.put(skillId, level);
    }

    public void remove(UUID skillId) {
        items.remove(skillId);
    }

    public void clear() {
        items.clear();
    }

    public Map<UUID, SkillLevel> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }
}
