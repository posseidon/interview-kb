package io.github.posseidon.knowledgebase.it.interview.skill;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Reads the skill-tree import spreadsheet into {@link SkillRow}s; knows nothing about persistence. */
@Component
class SkillXlsxReader {

    private static final int COL_NAME = 0;
    private static final int COL_PATH = 1;
    private static final int COL_DESCRIPTION = 4;
    private static final int COL_NOVICE = 5;
    private static final int COL_INTERMEDIATE = 6;
    private static final int COL_ADVANCED = 7;
    private static final int COL_EXPERT = 8;
    private static final int COL_POSITION = 13;

    /** Takes ownership of {@code in} and closes it. */
    List<SkillRow> read(InputStream in) {
        try (in; Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<SkillRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
                if (r == null) continue;
                String path = cellText(r.getCell(COL_PATH));
                if (path == null || path.isBlank()) continue;
                String name = cellText(r.getCell(COL_NAME));
                String description = cellText(r.getCell(COL_DESCRIPTION));
                String novice = cellText(r.getCell(COL_NOVICE));
                String intermediate = cellText(r.getCell(COL_INTERMEDIATE));
                String advanced = cellText(r.getCell(COL_ADVANCED));
                String expert = cellText(r.getCell(COL_EXPERT));
                Integer positionCount = parseIntOrNull(cellText(r.getCell(COL_POSITION)));
                rows.add(new SkillRow(name, path, description, positionCount, novice, intermediate, advanced, expert));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skills workbook", e);
        }
    }

    private static String cellText(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private static Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
