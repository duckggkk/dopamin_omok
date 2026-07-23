package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.game.adapter.out.persistence.GameJpaRepository;
import com.dopamin.omok.game.adapter.out.persistence.GameMoveJpaRepository;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.domain.TimeLimit;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게스트 하드 삭제(정리 스케줄러)가 <b>상대방의 대국 기록을 건드리지 않는지</b> 검증한다.
 * <p>
 * V38 이전에는 rooms.host_id / game_moves.player_id 가 ON DELETE CASCADE 라서
 * 게스트 방장을 지우면 방→대국→기보가 통째로, 게스트 참가자를 지우면 그가 둔 수만
 * 연쇄 삭제됐다. 이 테스트는 H2 스키마가 엔티티의 @OnDelete(SET_NULL) 로 생성되므로
 * 운영(V38 적용 후)과 같은 FK 동작을 실제로 태워 확인한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, UserPersistenceAdapter.class})
class GuestCleanupPreservesGamesTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private UserPersistenceAdapter userAdapter;
    @Autowired private GameJpaRepository gameJpaRepository;
    @Autowired private GameMoveJpaRepository gameMoveJpaRepository;

    private User member() {
        User user = User.createLocalUser("member@example.com", "encoded", "남는회원");
        user.verifyEmail();
        entityManager.persist(user);
        return user;
    }

    /** createdAt 은 @CreatedDate 라 persist 후 과거로 되돌려 '오래된 게스트'를 만든다. */
    private User staleGuest(String nickname) {
        User guest = User.createGuestUser("guest_" + nickname + "@guest.local", nickname);
        entityManager.persist(guest);
        entityManager.flush();
        entityManager.getEntityManager()
                .createQuery("UPDATE User u SET u.createdAt = :old WHERE u.id = :id")
                .setParameter("old", LocalDateTime.now().minusDays(30))
                .setParameter("id", guest.getId())
                .executeUpdate();
        return guest;
    }

    /** 게스트가 방장(흑), 회원이 백으로 5수까지 두고 끝난 대국 한 판. */
    private Game finishedGameHostedByGuest(User guest, User memberUser) {
        Room room = Room.create(guest, "GUESTROOM1", GameType.CLASSIC, OmokRule.FREESTYLE,
                TimeLimit.UNLIMITED, ByoyomiOption.NONE, false);
        room.startGame();
        entityManager.persist(room);

        Game game = Game.start(room, guest, memberUser);
        game.finish(memberUser);
        entityManager.persist(game);

        for (int i = 0; i < 5; i++) {
            boolean guestTurn = i % 2 == 0; // 흑(게스트) 선
            entityManager.persist(GameMove.of(game,
                    guestTurn ? guest : memberUser,
                    guestTurn ? StoneColor.BLACK : StoneColor.WHITE,
                    7, 3 + i, i + 1));
        }
        // 방은 닫혔고 game_players 도 정리된 상태(운영의 방 종료 흐름과 동일)
        room.close();
        entityManager.flush();
        return game;
    }

    @Test
    @DisplayName("게스트 방장을 삭제해도 방·대국·기보 전체가 남는다(사람 칸만 NULL)")
    void purgePreservesOpponentGameRecords() {
        User memberUser = member();
        User guest = staleGuest("게스트9999");
        Game game = finishedGameHostedByGuest(guest, memberUser);
        Long gameId = game.getId();
        Long roomId = game.getRoom().getId();

        int deleted = userAdapter.deleteGuestsCreatedBefore(LocalDateTime.now().minusDays(7));
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);

        // 방 행 생존 + 방장 칸만 비워짐 (V38 이전엔 방이 CASCADE 로 사라졌다)
        Room room = entityManager.find(Room.class, roomId);
        assertThat(room).isNotNull();
        assertThat(room.getHost()).isNull();

        // 대국 행 생존 + 게스트 자리만 비워짐
        Game survived = entityManager.find(Game.class, gameId);
        assertThat(survived).isNotNull();
        assertThat(survived.getBlackPlayer()).isNull();
        assertThat(survived.getWhitePlayer().getId()).isEqualTo(memberUser.getId());

        // 기보 5수 전부 생존 — 게스트가 둔 수(1·3·5수)도 남고 착수자만 NULL
        List<GameMove> moves = gameMoveJpaRepository.findByGameIdOrderByMoveNumberAsc(gameId);
        assertThat(moves).hasSize(5);
        assertThat(moves.get(0).getPlayer()).isNull();                              // 1수(게스트)
        assertThat(moves.get(1).getPlayer().getId()).isEqualTo(memberUser.getId()); // 2수(회원)

        // 회원의 다시보기 목록에 그 판이 계속 보인다(LEFT JOIN 수정 검증)
        assertThat(gameJpaRepository.findCompletedByUserId(memberUser.getId(), PageRequest.of(0, 10)))
                .extracting(Game::getId)
                .containsExactly(gameId);
    }

    @Test
    @DisplayName("활성 방에 참가 중인 게스트는 오래됐어도 정리에서 건너뛴다")
    void guestInActiveRoomIsNotPurged() {
        User guest = staleGuest("게스트8888");
        Room room = Room.create(guest, "LIVEROOM01", GameType.CLASSIC, OmokRule.FREESTYLE,
                TimeLimit.UNLIMITED, ByoyomiOption.NONE, false);
        entityManager.persist(room);
        // 살아 있는 방의 참가 기록(game_players) — 방이 닫힐 때만 지워진다
        entityManager.persist(GamePlayer.createHost(room, guest));
        entityManager.flush();

        int deleted = userAdapter.deleteGuestsCreatedBefore(LocalDateTime.now().minusDays(7));
        entityManager.clear();

        assertThat(deleted).isZero();
        assertThat(entityManager.find(User.class, guest.getId())).isNotNull();
    }
}
