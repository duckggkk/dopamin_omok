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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local MySQL benchmark. Skipped by default so CI and normal test runs do not need MySQL.
 *
 * <pre>
 * .\gradlew.bat test --tests "*HeadToHeadMySqlBenchmarkTest" `
 *   -Domok.bench.mysql=true `
 *   -Domok.bench.mysql.username=root `
 *   -Domok.bench.mysql.password=YOUR_PASSWORD
 * </pre>
 */
@Tag("manual")
@EnabledIfSystemProperty(named = "omok.bench.mysql", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        HeadToHeadPersistenceAdapter.class,
        FriendshipPersistenceAdapter.class
})
class HeadToHeadMySqlBenchmarkTest {

    private static final String DEFAULT_MYSQL_URL = "jdbc:mysql://localhost:3306/dopamin_omok_bench"
            + "?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Seoul"
            + "&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HeadToHeadPersistenceAdapter headToHeadAdapter;

    @Autowired
    private FriendshipPersistenceAdapter friendshipAdapter;

    @DynamicPropertySource
    static void mysqlBenchmarkProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", HeadToHeadMySqlBenchmarkTest::mysqlUrl);
        registry.add("spring.datasource.username",
                () -> firstNonBlank(
                        System.getProperty("omok.bench.mysql.username"),
                        System.getenv("OMOK_BENCH_MYSQL_USERNAME"),
                        "root"));
        registry.add("spring.datasource.password",
                () -> firstNonBlank(
                        System.getProperty("omok.bench.mysql.password"),
                        System.getenv("OMOK_BENCH_MYSQL_PASSWORD"),
                        ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void compareLegacyCountsAndBatchAggregationOnLocalMySql() {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        HeadToHeadTestDataSupport data = new HeadToHeadTestDataSupport(entityManager);

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

            System.out.printf(
                    "[HeadToHeadBenchmark] friends=%d legacy=%dms/%dq batch=%dms/%dq service=%dms/%dq%n",
                    friendCount,
                    legacy.elapsedMillis(), legacy.queryCount(),
                    batch.elapsedMillis(), batch.queryCount(),
                    service.elapsedMillis(), service.queryCount());
        }
    }

    private Scenario seedScenario(HeadToHeadTestDataSupport data, int friendCount) {
        User me = data.user("benchMe" + friendCount);
        List<Long> friendIds = data.acceptedFriendsWithGames(me, friendCount, 5, 3, 2).stream()
                .map(friend -> friend.user().getId())
                .toList();

        // Noise rows make the benchmark closer to a real games table without changing expected stats.
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

    private static String mysqlUrl() {
        String url = firstNonBlank(
                System.getProperty("omok.bench.mysql.url"),
                System.getenv("OMOK_BENCH_MYSQL_URL"),
                DEFAULT_MYSQL_URL);
        if (!url.contains("dopamin_omok_bench")) {
            throw new IllegalArgumentException("MySQL benchmark URL must point to a dopamin_omok_bench database: " + url);
        }
        return url;
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
}
