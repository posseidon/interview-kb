package io.github.posseidon.knowledgebase.it.interview.util;

import java.util.List;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

public final class Markdown {

  private static final Parser PARSER = Parser.builder()
      .extensions(List.of(TablesExtension.create())).build();
  private static final HtmlRenderer HTML = HtmlRenderer.builder()
      .extensions(List.of(TablesExtension.create())).build();
  private static final TextContentRenderer TEXT = TextContentRenderer.builder().build();

  private Markdown() {
  }

  public static String toHtml(String markdown) {
      if (markdown == null || markdown.isBlank()) {
          return "";
      }
    return HTML.render(PARSER.parse(markdown));
  }

  public static String toSnippet(String markdown, int maxLen) {
      if (markdown == null || markdown.isBlank()) {
          return "";
      }
    String plain = TEXT.render(PARSER.parse(markdown)).strip();
    return plain.length() <= maxLen ? plain : plain.substring(0, maxLen - 1) + "…";
  }
}
