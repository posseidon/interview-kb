package io.github.posseidon.knowledgebase.it.interview.interviews;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.domain.interview.Interview;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewView;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.SkillGroup;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.dto.question.SkillRef;
import io.github.posseidon.knowledgebase.it.interview.repo.InterviewRepository;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

  private final InterviewRepository interviewRepository;
  private final QuestionMapper questionMapper;

  public InterviewService(InterviewRepository interviewRepository, QuestionMapper questionMapper) {
    this.interviewRepository = interviewRepository;
    this.questionMapper = questionMapper;
  }

  private static DecisionDisplay decisionDisplay(Decision d) {
    return switch (d) {
      case NO_HIRE -> new DecisionDisplay("no-hire", "NO HIRE");
      case MAYBE -> new DecisionDisplay("maybe", "MAYBE");
      case GOOD_CANDIDATE -> new DecisionDisplay("good", "GOOD CANDIDATE");
    };
  }

  @Transactional(readOnly = true)
  public List<InterviewView> findAll() {
    return interviewRepository.findAllByOrderByDateAsc().stream()
        .map(this::toView)
        .toList();
  }

  private InterviewView toView(Interview iv) {
    List<QuestionView> questionViews = iv.getQuestions().stream()
        .sorted(Comparator.comparing(Question::getContent))
        .map(questionMapper::toView)
        .toList();

    Map<String, List<QuestionView>> grouped = new TreeMap<>();
    for (QuestionView qv : questionViews) {
      List<String> skillNames = qv.skills().isEmpty()
          ? List.of("General")
          : qv.skills().stream().map(SkillRef::name).toList();
      for (String skillName : skillNames) {
        grouped.computeIfAbsent(skillName, k -> new ArrayList<>()).add(qv);
      }
    }
    List<SkillGroup> questionsBySkill = grouped.entrySet().stream()
        .map(e -> new SkillGroup(e.getKey(), e.getValue()))
        .toList();

    DecisionDisplay display = decisionDisplay(iv.getDecision());
    return new InterviewView(
        iv.getId(),
        iv.getProjectCode(),
        iv.getDate().format(DATE_FMT),
        iv.getDecision(),
        display.cssClass(),
        display.label(),
        Markdown.toHtml(iv.getFeedback()),
        Markdown.toHtml(iv.getUpskillingPlan()),
        Markdown.toSnippet(iv.getFeedback(), 160),
        iv.getQuestions().size(),
        questionsBySkill
    );
  }

  @Transactional(readOnly = true)
  public Optional<InterviewView> findById(UUID id) {
    return interviewRepository.findById(id).map(this::toView);
  }

  private record DecisionDisplay(String cssClass, String label) {

  }
}
