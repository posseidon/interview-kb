package io.github.posseidon.knowledgebase.it.interview.skill;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class SkillIngestService {

    private static final Logger log = LoggerFactory.getLogger(SkillIngestService.class);
    private static final String PATH_SEPARATOR = " -> ";

    private static final int COL_NAME = 0;
    private static final int COL_PATH = 1;
    private static final int COL_DESCRIPTION = 4;
    private static final int COL_NOVICE = 5;
    private static final int COL_INTERMEDIATE = 6;
    private static final int COL_ADVANCED = 7;
    private static final int COL_EXPERT = 8;
    private static final int COL_POSITION = 13;

    private static final int CHUNK_SIZE = 200;

    // Kept well below spring.datasource.hikari.maximum-pool-size (5) so the
    // import never starves the app's other requests of a Supabase connection.
    private static final int CONCURRENCY = 3;

    private static final String UPSERT_SQL = """
            INSERT INTO skill (name, path, description, position_count, parent_id,
                                novice_criteria, intermediate_criteria, advanced_criteria, expert_criteria)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (path) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                position_count = EXCLUDED.position_count,
                parent_id = EXCLUDED.parent_id,
                novice_criteria = EXCLUDED.novice_criteria,
                intermediate_criteria = EXCLUDED.intermediate_criteria,
                advanced_criteria = EXCLUDED.advanced_criteria,
                expert_criteria = EXCLUDED.expert_criteria
            RETURNING id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public SkillIngestService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    /**
     * Upserts every row by path (matching the existing skill tree instead of
     * duplicating it), processing one tree depth at a time — since children
     * need their parent's freshly-assigned id — with the rows at each depth
     * split into chunks and upserted concurrently to keep any single
     * transaction short.
     */
    public int importFromXlsx(MultipartFile file) {
        List<Row> rows = readRows(file);

        Map<Integer, List<Row>> byDepth = new TreeMap<>();
        for (Row row : rows) {
            byDepth.computeIfAbsent(row.depth(), d -> new ArrayList<>()).add(row);
        }

        Map<String, UUID> idByPath = new ConcurrentHashMap<>();
        int imported = 0;

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            for (List<Row> levelRows : byDepth.values()) {
                List<Future<Integer>> futures = new ArrayList<>();
                for (List<Row> chunk : partition(levelRows, CHUNK_SIZE)) {
                    futures.add(executor.submit(() -> upsertChunk(chunk, idByPath)));
                }
                for (Future<Integer> future : futures) {
                    imported += future.get();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Skill import interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Skill import failed", e.getCause());
        } finally {
            executor.shutdown();
        }

        log.info("Imported {} skills", imported);
        return imported;
    }

    private int upsertChunk(List<Row> chunk, Map<String, UUID> idByPath) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            int count = 0;
            for (Row row : chunk) {
                UUID parentId = row.parentPath() == null ? null : idByPath.get(row.parentPath());
                if (row.parentPath() != null && parentId == null) {
                    log.warn("Skipping skill with unresolved parent path: {}", row.path());
                    continue;
                }
                UUID id = jdbcTemplate.queryForObject(UPSERT_SQL, UUID.class,
                        row.name(), row.path(), row.description(), row.positionCount(), parentId,
                        row.novice(), row.intermediate(), row.advanced(), row.expert());
                idByPath.put(row.path(), id);
                count++;
            }
            return count;
        });
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    private List<Row> readRows(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Row> rows = new ArrayList<>();
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
                rows.add(new Row(name, path, description, positionCount, novice, intermediate, advanced, expert));
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

    private record Row(String name, String path, String description, Integer positionCount,
                        String novice, String intermediate, String advanced, String expert) {
        int depth() {
            return (int) path.chars().filter(c -> c == '>').count();
        }

        String parentPath() {
            int idx = path.lastIndexOf(PATH_SEPARATOR);
            return idx < 0 ? null : path.substring(0, idx);
        }
    }
}
