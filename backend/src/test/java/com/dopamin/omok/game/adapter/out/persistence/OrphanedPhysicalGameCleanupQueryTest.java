package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.TimeLimit;
import com.dopamin.omok.user.domain.User;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class OrphanedPhysicalGameCleanupQueryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GameJpaRepository gameJpaRepository;

    @Test
    @DisplayName("진행 중인 피지컬 게임만 행 잠금으로 조회한다")
    void findsOnlyActivePhysicalGamesWithWriteLock() {
        User black = persistUser("orphan-black");
        User white = persistUser("orphan-white");

        Game activePhysical = persistGame("PHYSICAL01", GameType.PHYSICAL, black, white, true);
        persistGame("CLASSIC001", GameType.CLASSIC, black, white, true);
        persistGame("PHYSICAL02", GameType.PHYSICAL, black, white, false);
        entityManager.flush();
        entityManager.clear();

        List<Game> found = gameJpaRepository.findActivePhysicalGamesForUpdate();

        assertThat(found).extracting(Game::getId).containsExactly(activePhysical.getId());
        assertThat(found.getFirst().getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(entityManager.getEntityManager().getLockMode(found.getFirst()))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private User persistUser(String name) {
        User user = User.createLocalUser(name + "@test.local", "password", name);
        user.verifyEmail();
        entityManager.persist(user);
        return user;
    }

    private Game persistGame(String roomCode, GameType gameType, User black, User white, boolean active) {
        Room room = Room.create(black, roomCode, gameType, OmokRule.FREESTYLE,
                TimeLimit.UNLIMITED, ByoyomiOption.NONE, true);
        room.startGame();
        entityManager.persist(room);

        Game game = Game.start(room, black, white);
        if (!active) game.abandon();
        entityManager.persist(game);
        return game;
    }
}
