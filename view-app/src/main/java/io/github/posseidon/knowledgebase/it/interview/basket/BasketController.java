package io.github.posseidon.knowledgebase.it.interview.basket;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/basket")
public class BasketController {

  private final Basket basket;
  private final SkillRepository skillRepository;
  private final QuestionRepository questionRepository;

  public BasketController(Basket basket, SkillRepository skillRepository,
      QuestionRepository questionRepository) {
    this.basket = basket;
    this.skillRepository = skillRepository;
    this.questionRepository = questionRepository;
  }

  private static CheckoutItem toCheckoutItem(Question q, int number) {
    return new CheckoutItem(q.getId(), q.getContent(), q.isRequiresImpl(), number);
  }

  @Transactional(readOnly = true)
  @PostMapping("/items")
  public String addItem(@RequestParam UUID skillId, @RequestParam SkillLevel level) {
    Skill skill = skillRepository.findById(skillId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    basket.put(skillId, level);
    Skill parent = skill.getParent();
    return "redirect:/skills/" + (parent != null ? parent.getId() : skillId);
  }

  @PostMapping("/items/{skillId}/remove")
  public String removeItem(@PathVariable UUID skillId) {
    basket.remove(skillId);
    return "redirect:/basket";
  }

  @PostMapping("/clear")
  public String clear() {
    basket.clear();
    return "redirect:/basket";
  }

  @Transactional(readOnly = true)
  @GetMapping
  public String view(Model model) {
    Map<UUID, Skill> skillsById = skillsById(basket.items().keySet());
    List<BasketItemView> items = basket.items().entrySet().stream()
        .map(e -> {
          Skill skill = skillsById.get(e.getKey());
          return skill == null ? null
              : new BasketItemView(skill.getId(), skill.getName(), skill.getPath(), e.getValue());
        })
        .filter(Objects::nonNull)
        .toList();
    model.addAttribute("items", items);
    model.addAttribute("isEmpty", items.isEmpty());
    return "basket/basket";
  }

  private Map<UUID, Skill> skillsById(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return skillRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Skill::getId, s -> s));
  }

  /**
   * A question can be tagged with more than one skill, so the same question can match more than one
   * basket entry (e.g. a question tagged both "Java" and "Spring Boot"). {@code seenIds} ensures
   * each question is only rendered once — under whichever group it's matched first — so the "N
   * questions matched" total always equals the number of rows actually listed below it.
   */
  @Transactional(readOnly = true)
  @PostMapping("/checkout")
  public String checkout(Model model) {
    Map<UUID, Skill> skillsById = skillsById(basket.items().keySet());
    Set<UUID> seenIds = new LinkedHashSet<>();
    AtomicInteger counter = new AtomicInteger(0);
    List<SkillResultGroup> groups = new ArrayList<>();

    for (Map.Entry<UUID, SkillLevel> entry : basket.items().entrySet()) {
      Skill skill = skillsById.get(entry.getKey());
      if (skill == null) {
        continue;
      }
      SkillLevel level = entry.getValue();

      List<CheckoutItem> matched = questionRepository.findBySkillIdAndLevel(skill.getId(),
              level.name())
          .stream()
          .filter(q -> seenIds.add(q.getId()))
          .map(q -> toCheckoutItem(q, counter.incrementAndGet()))
          .toList();
      groups.add(new SkillResultGroup(skill.getName(), level, matched));
    }

    model.addAttribute("groups", groups);
    model.addAttribute("totalQuestions", seenIds.size());
    basket.clear();
    return "basket/checkout";
  }

  public record BasketItemView(UUID skillId, String skillName, String skillPath, SkillLevel level) {

  }

  public record SkillResultGroup(String skillName, SkillLevel level, List<CheckoutItem> questions) {

  }

  public record CheckoutItem(UUID id, String content, boolean requiresImpl, int number) {

  }
}
