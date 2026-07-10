package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.application.service.FriendService;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 MySQL 벤치마크 테스트.
 * 기본 실행에서는 스킵되어 CI와 일반 테스트 실행에 MySQL이 필요하지 않다.
 *
 * <pre>
 * .\gradlew.bat mysqlBenchmarkTest --rerun-tasks --console=plain
 * </pre>
 */
@Tag("manual")
@EnabledIfSystemProperty(named = "omok.bench.mysql", matches = "true")
@ActiveProfiles("mysql-benchmark")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = HeadToHeadMySqlBenchmarkTest.BenchmarkDatabaseGuard.class)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        HeadToHeadPersistenceAdapter.class,
        FriendshipPersistenceAdapter.class
})
class HeadToHeadMySqlBenchmarkTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HeadToHeadPersistenceAdapter headToHeadAdapter;

    @Autowired
    private FriendshipPersistenceAdapter friendshipAdapter;

    @DynamicPropertySource
    static void mysqlBenchmarkOverrides(DynamicPropertyRegistry registry) {
        String url = firstNonBlank(
                System.getProperty("omok.bench.mysql.url"),
                System.getenv("OMOK_BENCH_MYSQL_URL"));
        if (!url.isBlank()) {
            registry.add("spring.datasource.url", () -> {
                assertBenchmarkDatabaseUrl(url);
                return url;
            });
        }
    }

    @Test
    void compareLegacyCountsAndBatchAggregationOnLocalMySql() {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        HeadToHeadTestDataSupport data = new HeadToHeadTestDataSupport(entityManager);

        List<SummaryRow> rows = new ArrayList<>();
        for (int friendCount : List.of(1, 30, 100)) {
            Scenario scenario = seedScenario(data, friendCount);

            BenchmarkResult legacy = measure("legacy-3-counts", friendCount,
                    () -> legacyHeadToHeads(scenario.meId(), scenario.friendIds()));
            BenchmarkResult batch = measure("batch-group-by", friendCount,
                    () -> headToHeadAdapter.betweenMany(scenario.meId(), scenario.friendIds()));
            BenchmarkResult service = measure("friend-service", friendCount,
                    () -> friendService().getFriends(scenario.meId()));

            assertThat(legacy.queryCount()).isEqualTo(friendCount * 3L);
            assertThat(batch.queryCount()).isEqualTo(1L);
            assertThat(service.queryCount()).isEqualTo(2L);

            SummaryRow row = new SummaryRow(friendCount, legacy, batch, service);
            rows.add(row);
            System.out.println("[HeadToHeadBenchmark] " + row.toTextLine());
        }
        writeSummary(rows);
    }

    /**
     * 결과를 두 가지 형식으로 남긴다.
     * 텍스트 파일은 Gradle 태스크의 doLast에서 다시 출력해 콘솔 마지막에 측정값이 보이게 하고,
     * HTML 파일은 Gradle 테스트 리포트처럼 브라우저에서 표로 볼 수 있게 함께 생성한다.
     */
    private void writeSummary(List<SummaryRow> rows) {
        String path = System.getProperty("omok.bench.summary.file", "");
        if (path.isBlank()) {
            return;
        }
        try {
            Path textFile = Path.of(path);
            Files.createDirectories(textFile.getParent());
            Files.write(textFile, rows.stream().map(SummaryRow::toTextLine).toList());

            String htmlName = textFile.getFileName().toString().replaceFirst("\\.txt$", "") + ".html";
            Files.writeString(textFile.resolveSibling(htmlName), toHtml(rows));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toHtml(List<SummaryRow> rows) {
        StringBuilder tableRows = new StringBuilder();
        for (SummaryRow row : rows) {
            tableRows.append("""
                    <tr>
                      <td class="name">%d</td>
                      <td>%,d ms <span class="q">/ %,d queries</span></td>
                      <td>%,d ms <span class="q">/ %d query</span></td>
                      <td>%,d ms <span class="q">/ %d queries</span></td>
                    </tr>
                    """.formatted(
                    row.friends(),
                    row.legacy().elapsedMillis(), row.legacy().queryCount(),
                    row.batch().elapsedMillis(), row.batch().queryCount(),
                    row.service().elapsedMillis(), row.service().queryCount()));
        }
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <title>HeadToHead MySQL Benchmark</title>
                <style>
                  body { font-family: sans-serif; margin: 24px; color: #303030; }
                  h1 { font-size: 20px; }
                  .meta { color: #666; font-size: 13px; }
                  table { border-collapse: collapse; margin-top: 12px; }
                  th, td { border: 1px solid #d0d0d0; padding: 6px 16px; text-align: right; font-variant-numeric: tabular-nums; }
                  th { background: #efefef; }
                  th.name, td.name { text-align: left; }
                  .q { color: #999; font-size: 0.85em; }
                  .note { margin-top: 14px; color: #666; font-size: 13px; line-height: 1.6; }
                </style>
                </head>
                <body>
                <h1>HeadToHead MySQL Benchmark</h1>
                <p class="meta">측정 시각: %s · 워밍업 1회 적용 · 로컬 MySQL(dev)</p>
                <table>
                  <tr>
                    <th class="name">friends</th>
                    <th>legacy (친구당 COUNT 3회)</th>
                    <th>batch (GROUP BY 단일 집계)</th>
                    <th>service (친구목록 + 집계 전체)</th>
                  </tr>
                %s</table>
                <p class="note">
                  legacy = 친구마다 승/패/무 COUNT 쿼리 3개 (쿼리 수 = 친구수 × 3)<br>
                  batch = GROUP BY 한 방 집계 (쿼리 수 = 1)<br>
                  service = FriendService.getFriends 전체 경로 (쿼리 수 = 2)
                </p>
                </body>
                </html>
                """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                tableRows);
    }

    private Scenario seedScenario(HeadToHeadTestDataSupport data, int friendCount) {
        User me = data.user("benchMe" + friendCount);
        List<Long> friendIds = data.acceptedFriendsWithGames(me, friendCount, 5, 3, 2).stream()
                .map(friend -> friend.user().getId())
                .toList();

        // 기대 전적은 바꾸지 않으면서 실제 games 테이블에 더 가까운 조건을 만들기 위한 노이즈 row다.
        User unrelatedA = data.user("noiseA" + friendCount);
        User unrelatedB = data.user("noiseB" + friendCount);
        for (int i = 0; i < friendCount * 10; i++) {
            data.unrelatedFinishedGame(unrelatedA, unrelatedB);
        }

        entityManager.flush();
        entityManager.clear();
        return new Scenario(me.getId(), friendIds);
    }

    private BenchmarkResult measure(String name, int friendCount, Runnable action) {
        // 측정값에 연결, 쿼리 플랜, 버퍼 풀 같은 일회성 비용이 섞이지 않도록 먼저 한 번 실행한다.
        action.run();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.clear();

        long start = System.nanoTime();
        action.run();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        long queryCount = statistics.getPrepareStatementCount();
        if (queryCount <= 0) {
            throw new IllegalStateException(name + " did not execute queries for friendCount=" + friendCount);
        }
        entityManager.clear();
        return new BenchmarkResult(elapsedMillis, queryCount);
    }

    private Map<Long, HeadToHead> legacyHeadToHeads(Long meId, List<Long> friendIds) {
        return friendIds.stream().collect(java.util.stream.Collectors.toMap(
                friendId -> friendId,
                friendId -> new HeadToHead(
                        legacyCount(meId, friendId, LegacyResult.WIN),
                        legacyCount(meId, friendId, LegacyResult.LOSS),
                        legacyCount(meId, friendId, LegacyResult.DRAW))));
    }

    private int legacyCount(Long meId, Long friendId, LegacyResult result) {
        EntityManager em = entityManager.getEntityManager();
        String resultPredicate = switch (result) {
            case WIN -> "g.winner.id = :meId";
            case LOSS -> "g.winner.id = :friendId";
            case DRAW -> "g.status = :drawStatus";
        };
        String hql = """
                        select count(g)
                        from Game g
                        where ((g.blackPlayer.id = :meId and g.whitePlayer.id = :friendId)
                            or (g.blackPlayer.id = :friendId and g.whitePlayer.id = :meId))
                          and %s
                        """.formatted(resultPredicate);
        var query = em.createQuery(hql, Long.class)
                .setParameter("meId", meId)
                .setParameter("friendId", friendId);
        if (result == LegacyResult.DRAW) {
            query.setParameter("drawStatus", GameStatus.DRAW);
        }
        Long count = query.getSingleResult();
        return count.intValue();
    }

    private FriendService friendService() {
        return new FriendService(null, friendshipAdapter, friendshipAdapter, friendshipAdapter, headToHeadAdapter);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    static class BenchmarkDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String url = applicationContext.getEnvironment().getProperty("spring.datasource.url", "");
            assertBenchmarkDatabaseUrl(url);
        }
    }

    private static void assertBenchmarkDatabaseUrl(String url) {
        if (!url.contains("dopamin_omok_bench")) {
            throw new IllegalArgumentException("MySQL benchmark URL must point to a dopamin_omok_bench database: " + url);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private enum LegacyResult {
        WIN, LOSS, DRAW
    }

    private record Scenario(Long meId, List<Long> friendIds) {
    }

    private record BenchmarkResult(long elapsedMillis, long queryCount) {
    }

    private record SummaryRow(int friends, BenchmarkResult legacy, BenchmarkResult batch, BenchmarkResult service) {

        String toTextLine() {
            return String.format(
                    "friends=%-3d legacy=%4dms/%3dq batch=%4dms/%dq service=%4dms/%dq",
                    friends,
                    legacy.elapsedMillis(), legacy.queryCount(),
                    batch.elapsedMillis(), batch.queryCount(),
                    service.elapsedMillis(), service.queryCount());
        }
    }
}
