package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.domain.TimeLimit;
import com.dopamin.omok.user.domain.User;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동시 착수 방어선 2종을 검증한다.
 * <p>
 * 1) 애플리케이션 락 — {@code findActiveGameByRoomCodeForUpdate} 가 실제로 실행되는지.
 *    JPQL 은 부팅 시점에 검증되지만 {@code SELECT ... FOR UPDATE} 생성은 실행 시점이라,
 *    쿼리를 한 번도 호출하지 않으면 문법 오류를 잡지 못한다.
 * 2) DB 유니크 제약 — 락이 뚫렸을 때 중복 수순·중복 좌표가 실제로 거부되는지.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class GameMoveConcurrencyConstraintTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GameJpaRepository gameJpaRepository;

    private User black;
    private User white;
    private Room room;
    private Game game;

    @BeforeEach
    void setUp() {
        black = persistUser("black");
        white = persistUser("white");

        room = Room.create(black, "R000000001", GameType.CLASSIC, OmokRule.FREESTYLE,
                TimeLimit.UNLIMITED, ByoyomiOption.NONE, true);
        room.startGame();
        entityManager.persist(room);

        game = Game.start(room, black, white);
        entityManager.persist(game);
        entityManager.flush();
    }

    private User persistUser(String name) {
        User user = User.createLocalUser(name + "@test.local", "password", name);
        user.verifyEmail();
        entityManager.persist(user);
        return user;
    }

    @Test
    @DisplayName("행 잠금 조회가 실제로 실행되고 PESSIMISTIC_WRITE 락이 걸린다")
    void findActiveGameForUpdateExecutesAndAppliesLock() {
        entityManager.clear(); // 1차 캐시에 남은 엔티티가 아니라 실제 쿼리 결과를 보기 위해

        Optional<Game> found = gameJpaRepository.findActiveGameByRoomCodeForUpdate("R000000001");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(game.getId());
        // @Lock 이 조용히 무시되지 않았는지 확인한다.
        // 쿼리가 성공하는 것만으로는 SELECT ... FOR UPDATE 가 나갔다는 증거가 되지 않는다.
        assertThat(entityManager.getEntityManager().getLockMode(found.get()))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("게임이 끝나면 행 잠금 조회도 비어 있다 — 락 대기 중 상대가 이긴 경우 거절된다")
    void findActiveGameForUpdateReturnsEmptyWhenFinished() {
        game.finish(black);
        entityManager.flush();
        entityManager.clear();

        assertThat(gameJpaRepository.findActiveGameByRoomCodeForUpdate("R000000001")).isEmpty();
    }

    @Test
    @DisplayName("같은 게임에 같은 수순 번호는 저장할 수 없다")
    void duplicateMoveNumberIsRejected() {
        entityManager.persist(GameMove.of(game, black, StoneColor.BLACK, 7, 7, 1));

        // 식별자 전략이 IDENTITY 라 persist 시점에 INSERT 가 바로 나간다.
        // 위반이 persist 에서 터질 수도, flush 에서 터질 수도 있으므로 둘 다 감싼다.
        assertThatThrownBy(() -> {
            // 동시 착수가 뚫렸을 때 생기는 형태 — 같은 moveNumber, 다른 좌표
            entityManager.persist(GameMove.of(game, black, StoneColor.BLACK, 8, 8, 1));
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("같은 게임에 같은 좌표는 저장할 수 없다")
    void duplicatePositionIsRejected() {
        entityManager.persist(GameMove.of(game, black, StoneColor.BLACK, 7, 7, 1));

        assertThatThrownBy(() -> {
            // 같은 자리에 다시 착수 — 클래식 오목은 돌을 제거하지 않으므로 항상 버그다
            entityManager.persist(GameMove.of(game, white, StoneColor.WHITE, 7, 7, 2));
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("정상 수순은 그대로 저장된다 — 제약이 정상 대국을 막지 않는다")
    void normalMovesAreAccepted() {
        entityManager.persist(GameMove.of(game, black, StoneColor.BLACK, 7, 7, 1));
        entityManager.persist(GameMove.of(game, white, StoneColor.WHITE, 7, 8, 2));
        entityManager.persist(GameMove.of(game, black, StoneColor.BLACK, 8, 7, 3));

        entityManager.flush();

        assertThat(entityManager.getEntityManager()
                .createQuery("SELECT COUNT(m) FROM GameMove m WHERE m.game.id = :id", Long.class)
                .setParameter("id", game.getId())
                .getSingleResult()).isEqualTo(3L);
    }
}
