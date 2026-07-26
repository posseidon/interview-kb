package io.github.posseidon.knowledgebase.it.interview.interview;

import java.util.List;

public record InterviewQuestion(
    String question,
    String type,             // "answer" | "coding"
    List<String> sources,    // validated source question UUIDs (opaque strings)
    List<String> followUps) {

}
