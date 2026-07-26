package io.github.posseidon.knowledgebase.it.interview.vectorstore;

/**
 * Poll response for the vector-store re-embedding job.
 */
public record VectorStoreReembedStatusView(boolean running, long total, long processed,
    String error) {

}
