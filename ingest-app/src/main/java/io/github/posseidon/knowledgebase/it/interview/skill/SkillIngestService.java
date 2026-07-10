package io.github.posseidon.knowledgebase.it.interview.skill;

import java.io.ByteArrayInputStream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class SkillIngestService {

  private static final Logger log = LoggerFactory.getLogger(SkillIngestService.class);

  private static final int CHUNK_SIZE = 200;

  // Kept well below spring.datasource.hikari.maximum-pool-size (5) so the
  // import never starves the app's other requests of a Supabase connection.
  private static final int CONCURRENCY = 3;

  private final SkillXlsxReader xlsxReader;
  private final SkillUpsertRepository upsertRepository;
  private final PlatformTransactionManager transactionManager;

  SkillIngestService(SkillXlsxReader xlsxReader,
      SkillUpsertRepository upsertRepository,
      PlatformTransactionManager transactionManager) {
    this.xlsxReader = xlsxReader;
    this.upsertRepository = upsertRepository;
    this.transactionManager = transactionManager;
  }

  private static <T> List<List<T>> partition(List<T> list) {
    List<List<T>> chunks = new ArrayList<>();
    for (int i = 0; i < list.size(); i += CHUNK_SIZE) {
      chunks.add(list.subList(i, Math.min(i + CHUNK_SIZE, list.size())));
    }
    return chunks;
  }

  /**
   * Runs {@link #importFromXlsx(byte[])} on its own thread and returns immediately. {@code content}
   * must be a fully-read, request-independent copy — the caller's original source (e.g. a servlet
   * {@code MultipartFile}) may no longer be valid once its HTTP request completes, which can happen
   * before this background import finishes.
   */
  void importFromXlsxAsync(byte[] content) {
    new Thread(() -> importFromXlsx(content)).start();
  }

  /**
   * Upserts every row by path (matching the existing skill tree instead of duplicating it),
   * processing one tree depth at a time — since children need their parent's freshly-assigned id —
   * with the rows at each depth split into chunks and upserted concurrently to keep any single
   * transaction short.
   */
  synchronized void importFromXlsx(byte[] content) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    Map<Integer, List<SkillRow>> byDepth = groupByDepth(xlsxReader.read(inputStream));
    Map<String, UUID> idByPath = new ConcurrentHashMap<>();
    int imported = 0;

    try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY)) {
      try {
        for (List<SkillRow> levelRows : byDepth.values()) {
          imported += upsertLevel(executor, levelRows, idByPath);
        }
      } finally {
        executor.shutdown();
      }
    }

    log.info("Imported {} skills", imported);
  }

  private Map<Integer, List<SkillRow>> groupByDepth(List<SkillRow> rows) {
    Map<Integer, List<SkillRow>> byDepth = new TreeMap<>();
    for (SkillRow row : rows) {
      byDepth.computeIfAbsent(row.depth(), d -> new ArrayList<>()).add(row);
    }
    return byDepth;
  }

  private int upsertLevel(ExecutorService executor, List<SkillRow> levelRows,
      Map<String, UUID> idByPath) {
    List<Future<Integer>> futures = new ArrayList<>();
    for (List<SkillRow> chunk : partition(levelRows)) {
      futures.add(executor.submit(() -> upsertChunk(chunk, idByPath)));
    }
    try {
      int count = 0;
      for (Future<Integer> future : futures) {
        count += future.get();
      }
      return count;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Skill import interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Skill import failed", e.getCause());
    }
  }

  private int upsertChunk(List<SkillRow> chunk, Map<String, UUID> idByPath) {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    Integer count = tx.execute(status -> {
      int upserted = 0;
      for (SkillRow row : chunk) {
        UUID parentId = row.parentPath() == null ? null : idByPath.get(row.parentPath());
        if (row.parentPath() != null && parentId == null) {
          log.warn("Skipping skill with unresolved parent path: {}", row.path());
          continue;
        }
        UUID id = upsertRepository.upsert(row, parentId);
        idByPath.put(row.path(), id);
        upserted++;
      }
      return upserted;
    });
    return count != null ? count : 0;
  }
}
