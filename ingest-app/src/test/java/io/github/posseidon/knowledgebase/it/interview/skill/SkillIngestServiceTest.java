package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class SkillIngestServiceTest {

  private SkillXlsxReader xlsxReader;
  private SkillUpsertRepository upsertRepository;
  private PlatformTransactionManager transactionManager;
  private SkillIngestService service;

  private static SkillRow row(String path) {
    return new SkillRow("name", path, null, null, null, null, null, null);
  }

  @BeforeEach
  void setUp() {
    xlsxReader = mock(SkillXlsxReader.class);
    upsertRepository = mock(SkillUpsertRepository.class);
    transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus status = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(any())).thenReturn(status);
    service = new SkillIngestService(xlsxReader, upsertRepository, transactionManager);
  }

  @Test
  void importsRootBeforeChildSoParentIdIsResolved() {
    SkillRow root = row("Backend");
    SkillRow child = row("Backend -> Java");
    when(xlsxReader.read(any())).thenReturn(List.of(root, child));

    UUID rootId = UUID.randomUUID();
    when(upsertRepository.upsert(eq(root), eq(null))).thenReturn(rootId);
    when(upsertRepository.upsert(eq(child), eq(rootId))).thenReturn(UUID.randomUUID());

    service.importFromXlsx(new byte[0]);

    verify(upsertRepository).upsert(root, null);
    verify(upsertRepository).upsert(child, rootId);
  }

  @Test
  void skipsRowWhoseParentPathNeverResolved() {
    SkillRow orphan = row("Missing -> Child");
    when(xlsxReader.read(any())).thenReturn(List.of(orphan));

    service.importFromXlsx(new byte[0]);

    verify(upsertRepository, never()).upsert(eq(orphan), any());
  }

  @Test
  void importFromXlsxAsyncRunsInBackground() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    when(xlsxReader.read(any())).thenAnswer(inv -> {
      latch.countDown();
      return List.of();
    });

    service.importFromXlsxAsync(new byte[0]);

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }
}
