package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.friend.application.dto.FriendResponse;
import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.application.service.FriendService;
import com.dopamin.omok.user.domain.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        HeadToHeadPersistenceAdapter.class,
        FriendshipPersistenceAdapter.class
})
class HeadToHeadBatchAggregationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HeadToHeadPersistenceAdapter headToHeadAdapter;

    @Autowired
    private FriendshipPersistenceAdapter friendshipAdapter;

    private HeadToHeadTestDataSupport data;

    @BeforeEach
    void setUp() {
        data = new HeadToHeadTestDataSupport(entityManager);
        statistics().setStatisticsEnabled(true);
    }

    @Test
    void betweenManyReturnsSameStatsAsSingleLookup() {
        User me = data.user("me");
        HeadToHeadTestDataSupport.FriendCase first = data.acceptedFriendWithGames(me, 3, 1, 2);
        HeadToHeadTestDataSupport.FriendCase second = data.acceptedFriendWithGames(me, 0, 4, 1);
        User noGameFriend = data.user("nogame");

        entityManager.flush();
        entityManager.clear();

        Map<Long, HeadToHead> stats = headToHeadAdapter.betweenMany(
                me.getId(),
                List.of(first.user().getId(), second.user().getId(), noGameFriend.getId()));

        assertThat(stats).containsEntry(first.user().getId(), first.expected());
        assertThat(stats).containsEntry(second.user().getId(), second.expected());
        assertThat(stats).doesNotContainKey(noGameFriend.getId());
        assertThat(headToHeadAdapter.between(me.getId(), first.user().getId())).isEqualTo(first.expected());
        assertThat(headToHeadAdapter.between(me.getId(), noGameFriend.getId())).isEqualTo(HeadToHead.empty());
    }

    @Test
    void betweenManyUsesOneQueryForOneHundredFriends() {
        User me = data.user("me");
        List<Long> friendIds = data.acceptedFriendsWithGames(me, 100, 2, 1, 1).stream()
                .map(friend -> friend.user().getId())
                .toList();

        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        Map<Long, HeadToHead> stats = headToHeadAdapter.betweenMany(me.getId(), friendIds);

        assertThat(stats).hasSize(100);
        assertThat(stats.values()).allSatisfy(head -> assertThat(head).isEqualTo(new HeadToHead(2, 1, 1)));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void getFriendsKeepsQueryCountConstantForOneHundredFriends() {
        User me = data.user("me");
        data.acceptedFriendsWithGames(me, 100, 2, 1, 1);

        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        FriendService service = new FriendService(null, friendshipAdapter, friendshipAdapter, friendshipAdapter, headToHeadAdapter);
        List<FriendResponse> responses = service.getFriends(me.getId());

        assertThat(responses).hasSize(100);
        assertThat(responses).allSatisfy(response ->
                assertThat(response.headToHead()).isEqualTo(new HeadToHead(2, 1, 1)));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
