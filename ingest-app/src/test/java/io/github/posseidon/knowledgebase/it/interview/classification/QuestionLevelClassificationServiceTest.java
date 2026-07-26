package io.github.posseidon.knowledgebase.it.interview.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class QuestionLevelClassificationServiceTest {

  private QuestionRepository questionRepository;
  private QuestionLevelClassifier classifier;
  private QuestionLevelClassificationProgress progress;
  private QuestionLevelClassificationService service;

  private static Question questionWith(String content, Skill... skills) {
    Question question = new Question(content, ContentHash.sha256(content));
    question.setId(UUID.randomUUID());
    question.setSkills(new HashSet<>(Set.of(skills)));
    return question;
  }

  private static Skill skillWith(String name) {
    Skill skill = new Skill();
    skill.setName(name);
    return skill;
  }

  private static void awaitCompletion(QuestionLevelClassificationProgress progress)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (progress.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    classifier = mock(QuestionLevelClassifier.class);
    progress = new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    service = new QuestionLevelClassificationService(questionRepository, classifier, progress);
  }

  private void mockOnePage(Question... questions) {
    when(questionRepository.count()).thenReturn((long) questions.length);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(questions), PageRequest.of(0, 50), questions.length));
  }

  @Test
  void skipsQuestionWithNoSkillsWithoutCallingTheClassifier() throws InterruptedException {
    Question question = questionWith("Standalone question");
    mockOnePage(question);

    service.classifyAllAsync();
    awaitCompletion(progress);

    verifyNoInteractions(classifier);
    verify(questionRepository, never()).save(any());
    assertThat(progress.skipped()).isEqualTo(1);
    assertThat(progress.processed()).isEqualTo(1);
  }

  @Test
  void classifiesQuestionAndSavesTheReturnedLevel() throws InterruptedException {
    Question question = questionWith("How to ensure thread-safety?", skillWith("Java"));
    mockOnePage(question);
    when(classifier.classify(question)).thenReturn(Optional.of(
        new QuestionLevelClassification(SkillLevel.ADVANCED, "needs concurrency internals")));

    service.classifyAllAsync();
    awaitCompletion(progress);

    ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
    verify(questionRepository).save(saved.capture());
    assertThat(saved.getValue().getLevel()).isEqualTo(SkillLevel.ADVANCED);
    assertThat(progress.processed()).isEqualTo(1);
    assertThat(progress.failed()).isZero();
  }

  @Test
  void classifierFailureLeavesLevelUnchangedAndIncrementsFailedCounter()
      throws InterruptedException {
    Question question = questionWith("How to ensure thread-safety?", skillWith("Java"));
    mockOnePage(question);
    when(classifier.classify(question)).thenReturn(Optional.empty());

    service.classifyAllAsync();
    awaitCompletion(progress);

    verify(questionRepository, never()).save(any());
    assertThat(question.getLevel()).isNull();
    assertThat(progress.failed()).isEqualTo(1);
    assertThat(progress.processed()).isEqualTo(1);
  }

  @Test
  void secondTriggerWhileRunningIsRejected() {
    when(questionRepository.count()).thenReturn(0L);
    progress.tryStart();

    boolean started = service.classifyAllAsync();

    assertThat(started).isFalse();
  }
}
