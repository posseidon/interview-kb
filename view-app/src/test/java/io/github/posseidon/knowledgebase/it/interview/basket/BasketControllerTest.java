package io.github.posseidon.knowledgebase.it.interview.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class BasketControllerTest {

  private SkillRepository skillRepository;
  private QuestionRepository questionRepository;
  private Basket basket;
  private BasketController controller;

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    questionRepository = mock(QuestionRepository.class);
    basket = new Basket();
    controller = new BasketController(basket, skillRepository, questionRepository,
        new QuestionMapper());
  }

  @Test
  void addItemRedirectsToParentWhenSkillHasParent() {
    Skill parent = new Skill("Backend", "Backend", null, null, null);
    parent.setId(UUID.randomUUID());
    Skill child = new Skill("Java", "Backend -> Java", null, null, parent);
    child.setId(UUID.randomUUID());
    when(skillRepository.findById(child.getId())).thenReturn(Optional.of(child));

    String result = controller.addItem(child.getId(), SkillLevel.ADVANCED);

    assertThat(result).isEqualTo("redirect:/skills/" + parent.getId());
    assertThat(basket.items()).containsEntry(child.getId(), SkillLevel.ADVANCED);
  }

  @Test
  void addItemRedirectsToSkillItselfWhenNoParent() {
    Skill root = new Skill("Backend", "Backend", null, null, null);
    root.setId(UUID.randomUUID());
    when(skillRepository.findById(root.getId())).thenReturn(Optional.of(root));

    String result = controller.addItem(root.getId(), SkillLevel.NOVICE);

    assertThat(result).isEqualTo("redirect:/skills/" + root.getId());
  }

  @Test
  void addItemThrowsNotFoundWhenSkillMissing() {
    UUID missingId = UUID.randomUUID();
    when(skillRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.addItem(missingId, SkillLevel.NOVICE))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void removeItemRemovesFromBasketAndRedirects() {
    UUID skillId = UUID.randomUUID();
    basket.put(skillId, SkillLevel.NOVICE);

    String result = controller.removeItem(skillId);

    assertThat(result).isEqualTo("redirect:/basket");
    assertThat(basket.isEmpty()).isTrue();
  }

  @Test
  void clearEmptiesBasketAndRedirects() {
    basket.put(UUID.randomUUID(), SkillLevel.NOVICE);

    String result = controller.clear();

    assertThat(result).isEqualTo("redirect:/basket");
    assertThat(basket.isEmpty()).isTrue();
  }

  @Test
  void viewBuildsItemsSkippingSkillsNoLongerFound() {
    Skill skill = new Skill("Java", "Java", null, null, null);
    skill.setId(UUID.randomUUID());
    UUID missingSkillId = UUID.randomUUID();
    basket.put(skill.getId(), SkillLevel.ADVANCED);
    basket.put(missingSkillId, SkillLevel.NOVICE);
    when(skillRepository.findAllById(anyCollection())).thenReturn(List.of(skill));

    Model model = new ExtendedModelMap();
    String view = controller.view(model);

    assertThat(view).isEqualTo("basket/basket");
    @SuppressWarnings("unchecked")
    List<BasketController.BasketItemView> items =
        (List<BasketController.BasketItemView>) model.getAttribute("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).skillId()).isEqualTo(skill.getId());
    assertThat(model.getAttribute("isEmpty")).isEqualTo(false);
  }

  @Test
  void viewReturnsEmptyWhenBasketHasNoResolvableSkills() {
    Model model = new ExtendedModelMap();

    controller.view(model);

    assertThat(model.getAttribute("isEmpty")).isEqualTo(true);
  }

  @Test
  void checkoutGroupsQuestionsBySkillAndClearsBasket() {
    Skill skill = new Skill("Java", "Java", null, null, null);
    skill.setId(UUID.randomUUID());
    basket.put(skill.getId(), SkillLevel.ADVANCED);
    when(skillRepository.findAllById(anyCollection())).thenReturn(List.of(skill));
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findBySkillIdAndLevel(skill.getId(), "ADVANCED"))
        .thenReturn(List.of(question));

    Model model = new ExtendedModelMap();
    String view = controller.checkout(model);

    assertThat(view).isEqualTo("basket/checkout");
    assertThat(model.getAttribute("totalQuestions")).isEqualTo(1);
    assertThat(basket.isEmpty()).isTrue();
  }

  @Test
  void checkoutSkipsBasketEntriesWithMissingSkill() {
    UUID missingSkillId = UUID.randomUUID();
    basket.put(missingSkillId, SkillLevel.NOVICE);
    when(skillRepository.findAllById(anyCollection())).thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.checkout(model);

    assertThat(model.getAttribute("totalQuestions")).isEqualTo(0);
  }
}
