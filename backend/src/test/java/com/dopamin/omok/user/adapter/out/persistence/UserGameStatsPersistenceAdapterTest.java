package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.TimeLimit;
import com.dopamin.omok.user.application.dto.ModeStats;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모드별 전적 집계를 검증한다.
 * <p>
 * 랭킹은 사람마다 승/패/무를 따로 세면 100명 × 3 = 300 쿼리가 나가서 한 번에 모아 오도록 바꿨다.
 * 묶어서 세는 방식(CASE WHEN 합계 + GROUP BY)이 한 명씩 세던 결과와 같은지,
 * 특히 <b>흑/백 양쪽으로 둔 대국이 합산</b>되는지가 이 테스트의 핵심이다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, UserGameStatsPersistenceAdapter.class})
class UserGameStatsPersistenceAdapterTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private UserGameStatsPersistenceAdapter adapter;

    private User alice;
    private User bob;
    private User rookie;

    @BeforeEach
    void setUp() {
        alice = member("alice@example.com", "앨리스");
        bob = member("bob@example.com", "밥");
        rookie = member("rookie@example.com", "무전적");
    }

    private User member(String email, String nickname) {
        User user = User.createLocalUser(email, "encoded", nickname);
        user.verifyEmail();
        entityManager.persist(user);
        return user;
    }

    private User guest() {
        User guest = User.createGuestUser("guest@guest.local", "게스트1234");
        entityManager.persist(guest);
        return guest;
    }

    /** black 이 흑, white 가 백인 대국 한 판. winner 가 null 이면 무승부로 끝낸다. */
    private Game game(GameType type, User black, User white, User winner) {
        Room room = Room.create(black, "ROOM" + System.nanoTime() % 100000, type,
                OmokRule.FREESTYLE, TimeLimit.UNLIMITED, ByoyomiOption.NONE, false);
        room.startGame();
        entityManager.persist(room);

        Game game = Game.start(room, black, white);
        if (winner == null) {
            game.draw();
        } else {
            game.finish(winner);
        }
        entityManager.persist(game);
        room.close();
        return game;
    }

    @Test
    @DisplayName("흑으로 둔 판과 백으로 둔 판의 전적이 합산된다")
    void mergesGamesPlayedAsBlackAndWhite() {
        game(GameType.CLASSIC, alice, bob, alice); // 앨리스 흑 승
        game(GameType.CLASSIC, alice, bob, bob);   // 앨리스 흑 패
        game(GameType.CLASSIC, bob, alice, alice); // 앨리스 백 승
        game(GameType.CLASSIC, bob, alice, null);  // 앨리스 백 무
        entityManager.flush();
        entityManager.clear();

        ModeStats stats = adapter.statsByMode(alice.getId(), GameType.CLASSIC);

        assertThat(stats.wins()).isEqualTo(2);
        assertThat(stats.losses()).isEqualTo(1);
        assertThat(stats.draws()).isEqualTo(1);
        assertThat(stats.totalGames()).isEqualTo(4);
        assertThat(stats.winRate()).isEqualTo(50); // 2/4
    }

    @Test
    @DisplayName("배치 조회 결과가 한 명씩 조회한 결과와 같다")
    void batchMatchesSingleLookup() {
        game(GameType.CLASSIC, alice, bob, alice);
        game(GameType.CLASSIC, bob, alice, bob);
        game(GameType.CLASSIC, alice, bob, null);
        entityManager.flush();
        entityManager.clear();

        Map<Long, ModeStats> batch =
                adapter.statsByModeForUsers(List.of(alice.getId(), bob.getId()), GameType.CLASSIC);

        assertThat(batch.get(alice.getId())).isEqualTo(adapter.statsByMode(alice.getId(), GameType.CLASSIC));
        assertThat(batch.get(bob.getId())).isEqualTo(adapter.statsByMode(bob.getId(), GameType.CLASSIC));
        assertThat(batch.get(alice.getId()).wins()).isEqualTo(1);
        assertThat(batch.get(bob.getId()).wins()).isEqualTo(1);
    }

    @Test
    @DisplayName("전적이 없는 사용자는 결과 맵에서 빠진다 — 호출부가 0전으로 채운다")
    void userWithoutGamesIsAbsent() {
        game(GameType.CLASSIC, alice, bob, alice);
        entityManager.flush();
        entityManager.clear();

        Map<Long, ModeStats> batch =
                adapter.statsByModeForUsers(List.of(alice.getId(), rookie.getId()), GameType.CLASSIC);

        assertThat(batch).containsKey(alice.getId());
        assertThat(batch).doesNotContainKey(rookie.getId());
        assertThat(adapter.statsByMode(rookie.getId(), GameType.CLASSIC)).isEqualTo(ModeStats.of(0, 0, 0));
    }

    @Test
    @DisplayName("다른 모드의 대국은 섞이지 않는다")
    void countsOnlyRequestedMode() {
        game(GameType.CLASSIC, alice, bob, alice);
        game(GameType.PHYSICAL, alice, bob, bob);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.statsByMode(alice.getId(), GameType.CLASSIC).wins()).isEqualTo(1);
        assertThat(adapter.statsByMode(alice.getId(), GameType.CLASSIC).losses()).isZero();
        assertThat(adapter.statsByMode(alice.getId(), GameType.PHYSICAL).losses()).isEqualTo(1);
    }

    @Test
    @DisplayName("게스트와 둔 판은 전적에서 빠진다 — 레이팅 반영 규칙과 같다")
    void excludesGuestGames() {
        game(GameType.CLASSIC, alice, guest(), alice);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.statsByMode(alice.getId(), GameType.CLASSIC)).isEqualTo(ModeStats.of(0, 0, 0));
        assertThat(adapter.statsByModeForUsers(List.of(alice.getId()), GameType.CLASSIC)).isEmpty();
    }

    @Test
    @DisplayName("빈 명단으로 조회하면 쿼리 없이 빈 맵을 준다")
    void emptyIdsShortCircuits() {
        assertThat(adapter.statsByModeForUsers(List.of(), GameType.CLASSIC)).isEmpty();
    }
}
