package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SkillXlsxReaderTest {

  private static final int COL_NAME = 0;
  private static final int COL_PATH = 1;
  private static final int COL_DESCRIPTION = 4;
  private static final int COL_NOVICE = 5;
  private static final int COL_INTERMEDIATE = 6;
  private static final int COL_ADVANCED = 7;
  private static final int COL_EXPERT = 8;
  private static final int COL_POSITION = 13;

  private final SkillXlsxReader reader = new SkillXlsxReader();

  private static byte[] workbookBytes(java.util.function.Consumer<Sheet> populate)
      throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet();
      sheet.createRow(0); // header, skipped by the reader
      populate.accept(sheet);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        workbook.write(out);
        return out.toByteArray();
      }
    }
  }

  @Test
  void readsFullRowIntoSkillRow() throws IOException {
    byte[] bytes = workbookBytes(sheet -> {
      Row row = sheet.createRow(1);
      row.createCell(COL_NAME).setCellValue("Java");
      row.createCell(COL_PATH).setCellValue("Backend -> Java");
      row.createCell(COL_DESCRIPTION).setCellValue("description");
      row.createCell(COL_NOVICE).setCellValue("novice");
      row.createCell(COL_INTERMEDIATE).setCellValue("intermediate");
      row.createCell(COL_ADVANCED).setCellValue("advanced");
      row.createCell(COL_EXPERT).setCellValue("expert");
      row.createCell(COL_POSITION).setCellValue(5);
    });

    List<SkillRow> rows = reader.read(new ByteArrayInputStream(bytes));

    assertThat(rows).hasSize(1);
    SkillRow row = rows.get(0);
    assertThat(row.name()).isEqualTo("Java");
    assertThat(row.path()).isEqualTo("Backend -> Java");
    assertThat(row.description()).isEqualTo("description");
    assertThat(row.novice()).isEqualTo("novice");
    assertThat(row.intermediate()).isEqualTo("intermediate");
    assertThat(row.advanced()).isEqualTo("advanced");
    assertThat(row.expert()).isEqualTo("expert");
    assertThat(row.positionCount()).isEqualTo(5);
  }

  @Test
  void skipsRowsWithNoPath() throws IOException {
    byte[] bytes = workbookBytes(sheet -> {
      Row row = sheet.createRow(1);
      row.createCell(COL_NAME).setCellValue("Java");
      // no path cell set
    });

    List<SkillRow> rows = reader.read(new ByteArrayInputStream(bytes));

    assertThat(rows).isEmpty();
  }

  @Test
  void skipsCompletelyEmptyRows() throws IOException {
    byte[] bytes = workbookBytes(sheet -> sheet.createRow(1));

    List<SkillRow> rows = reader.read(new ByteArrayInputStream(bytes));

    assertThat(rows).isEmpty();
  }

  @Test
  void unparsablePositionCountBecomesNull() throws IOException {
    byte[] bytes = workbookBytes(sheet -> {
      Row row = sheet.createRow(1);
      row.createCell(COL_PATH).setCellValue("Backend");
      row.createCell(COL_POSITION).setCellValue("not-a-number");
    });

    List<SkillRow> rows = reader.read(new ByteArrayInputStream(bytes));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).positionCount()).isNull();
  }

  @Test
  void wrapsIoExceptionAsUnchecked() {
    ByteArrayInputStream garbage = new ByteArrayInputStream("not an xlsx file".getBytes());

    org.junit.jupiter.api.Assertions.assertThrows(UncheckedIOException.class,
        () -> reader.read(garbage));
  }
}
