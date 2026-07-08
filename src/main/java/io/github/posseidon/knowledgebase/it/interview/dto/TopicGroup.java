package io.github.posseidon.knowledgebase.it.interview.dto;

import java.util.List;

public record TopicGroup(String topic, List<QuestionView> questions) {}
