package io.github.posseidon.knowledgebase.it.interview.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownTest {

  @Test
  void toHtmlRendersBasicMarkdown() {
    String html = Markdown.toHtml("**bold** text");

    assertThat(html).contains("<strong>bold</strong>");
  }

  @Test
  void toHtmlReturnsEmptyStringForNull() {
    assertThat(Markdown.toHtml(null)).isEmpty();
  }

  @Test
  void toHtmlReturnsEmptyStringForBlank() {
    assertThat(Markdown.toHtml("   ")).isEmpty();
  }

  @Test
  void toSnippetReturnsEmptyStringForNull() {
    assertThat(Markdown.toSnippet(null, 100)).isEmpty();
  }

  @Test
  void toSnippetReturnsEmptyStringForBlank() {
    assertThat(Markdown.toSnippet("   ", 100)).isEmpty();
  }

  @Test
  void toSnippetReturnsPlainTextUnchangedWhenShortEnough() {
    String snippet = Markdown.toSnippet("**bold** text", 100);

    assertThat(snippet).isEqualTo("bold text");
  }

  @Test
  void toSnippetTruncatesAndAddsEllipsisWhenTooLong() {
    String longText = "word ".repeat(50).strip();

    String snippet = Markdown.toSnippet(longText, 20);

    assertThat(snippet).hasSize(20);
    assertThat(snippet).endsWith("…");
  }
}
