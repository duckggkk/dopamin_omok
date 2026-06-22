package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.effect.BombEffect;
import com.dopamin.omok.game.physical.domain.effect.CraterEffect;
import com.dopamin.omok.game.physical.domain.effect.PhysicalItemEffectRegistry;
import com.dopamin.omok.game.physical.domain.effect.RemoveStoneEffect;
import com.dopamin.omok.game.physical.domain.effect.SpeedBoostEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PhysicalOmokEngineTest {

    private static final int SIZE = 14;

    private final PhysicalOmokProperties props = buildProps();
    private final PhysicalOmokEngine engine = new PhysicalOmokEngine(
            props,
            new PhysicalItemEffectRegistry(List.of(
                    new SpeedBoostEffect(props), new CraterEffect(), new BombEffect(), new RemoveStoneEffect())),
            new Random(42));

    private static PhysicalOmokProperties buildProps() {
        Map<PhysicalItemType, Integer> weights = new EnumMap<>(PhysicalItemType.class);
        weights.put(PhysicalItemType.SPEED_BOOST, 50);
        weights.put(PhysicalItemType.CRATER, 30);
        weights.put(PhysicalItemType.BOMB, 20);
        // boardSize=14, winCount=5, move=100, place=300, destroy=2000, winSettle=400, countdown=3000,
        // tick=60, spawnInterval=1000, maxItems=3, boostDuration=5000, boostMoveCd=50
        return new PhysicalOmokProperties(SIZE, 5, 100, 300, 2000, 400, 3000, 60, 1000, 3, 5000, 50, weights, false);
    }

    private PhysicalGame newGame() {
        PhysicalGame game = new PhysicalGame("ROOM", 1L, SIZE, 5, 0L, 0L);
        game.addPlayer(new PhysicalPlayer(1L, "흑", StoneColor.BLACK, null, null, null, 5, 5));
        game.addPlayer(new PhysicalPlayer(2L, "백", StoneColor.WHITE, null, null, null, 8, 8));
        return game;
    }

    private PhysicalPlayer black(PhysicalGame g) {
        return g.playerOf(1L);
    }

    @Test
    @DisplayName("착수: 빈칸에 내 색 돌이 놓인다")
    void placePutsStone() {
        PhysicalGame g = newGame();
        engine.place(g, black(g), 1000);
        assertThat(g.board().stoneAt(5, 5)).isEqualTo(StoneColor.BLACK);
    }

    @Test
    @DisplayName("착수 쿨다운: 쿨다운 전에는 다시 둘 수 없다")
    void placeRespectsCooldown() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g);
        engine.place(g, p, 1000);          // (5,5) 착수
        p.moveTo(6, 5, 1000);              // 빈칸으로 이동
        engine.place(g, p, 1100);          // 쿨다운(300) 전 → 실패
        assertThat(g.board().stoneAt(6, 5)).isNull();
        engine.place(g, p, 1400);          // 쿨다운 후 → 성공
        assertThat(g.board().stoneAt(6, 5)).isEqualTo(StoneColor.BLACK);
    }

    @Test
    @DisplayName("착수: 분화구에는 둘 수 없다")
    void cannotPlaceOnCrater() {
        PhysicalGame g = newGame();
        g.board().crater(5, 5);
        engine.place(g, black(g), 1000);
        assertThat(g.board().stoneAt(5, 5)).isNull();
    }

    @Test
    @DisplayName("5목 완성 시 즉시 끝나지 않고, settle 동안 유지되면 승리 확정된다")
    void placeStartsPendingWinThenConfirms() {
        PhysicalGame g = newGame();
        for (int x = 0; x < 4; x++) g.board().place(x, 0, StoneColor.BLACK);
        PhysicalPlayer p = black(g);
        p.moveTo(4, 0, 0);

        boolean formed = engine.place(g, p, 1000);
        assertThat(formed).isTrue();
        assertThat(g.status()).isEqualTo(GameStatus.IN_PROGRESS); // settle 전엔 미확정
        assertThat(g.pendingWinColor()).isEqualTo(StoneColor.BLACK);

        engine.settlePendingWin(g, 1000 + props.winSettleMs() + 1); // 유지 → 확정
        assertThat(g.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(g.winnerColor()).isEqualTo(StoneColor.BLACK);
        assertThat(g.winnerUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("settle 동안 상대가 라인을 끊으면 승리가 무효 처리된다(오프닝 러시 방지)")
    void pendingWinCancelledIfBroken() {
        PhysicalGame g = newGame();
        for (int x = 0; x < 4; x++) g.board().place(x, 0, StoneColor.BLACK);
        PhysicalPlayer p = black(g);
        p.moveTo(4, 0, 0);
        engine.place(g, p, 1000); // 5목 → 확정 대기

        g.board().removeStone(2, 0); // 상대가 가운데 돌 제거 → 라인 끊김
        engine.settlePendingWin(g, 1000 + props.winSettleMs() + 1);

        assertThat(g.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(g.hasPendingWin()).isFalse();
    }

    @Test
    @DisplayName("파괴: 현재 칸의 상대 돌만, 2초 쿨다운으로 제거된다")
    void destroyOnlyOpponentWithCooldown() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        g.board().place(5, 5, StoneColor.WHITE);
        engine.destroy(g, p, 3000);  // 상대 돌 → 제거
        assertThat(g.board().stoneAt(5, 5)).isNull();

        g.board().place(5, 5, StoneColor.WHITE);
        engine.destroy(g, p, 3500);  // 쿨다운(2000) 전 → 유지
        assertThat(g.board().stoneAt(5, 5)).isEqualTo(StoneColor.WHITE);

        engine.destroy(g, p, 5100);  // 쿨다운 후 → 제거
        assertThat(g.board().stoneAt(5, 5)).isNull();
    }

    @Test
    @DisplayName("파괴: 내 돌은 파괴할 수 없다")
    void cannotDestroyOwnStone() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g);
        g.board().place(5, 5, StoneColor.BLACK);
        engine.destroy(g, p, 3000);
        assertThat(g.board().stoneAt(5, 5)).isEqualTo(StoneColor.BLACK);
    }

    @Test
    @DisplayName("아이템(분화구): 현재 칸을 영구 착수 불가로 만든다")
    void craterItem() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g);
        g.board().place(5, 5, StoneColor.WHITE);
        p.hold(PhysicalItemType.CRATER);
        engine.useItem(g, p, 1000);
        assertThat(g.board().isBlocked(5, 5)).isTrue();
        assertThat(g.board().isPlaceable(5, 5)).isFalse();
        assertThat(p.getHeldItem()).isNull();
    }

    @Test
    @DisplayName("아이템(폭탄): 주변 3x3 모든 돌을 제거한다")
    void bombItem() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        g.board().place(4, 4, StoneColor.BLACK);
        g.board().place(5, 5, StoneColor.WHITE);
        g.board().place(6, 6, StoneColor.WHITE);
        p.hold(PhysicalItemType.BOMB);
        engine.useItem(g, p, 1000);
        assertThat(g.board().stoneAt(4, 4)).isNull();
        assertThat(g.board().stoneAt(5, 5)).isNull();
        assertThat(g.board().stoneAt(6, 6)).isNull();
    }

    @Test
    @DisplayName("아이템(속도): 부스트 동안 이동 쿨다운이 짧아진다")
    void speedBoostItem() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g);
        p.hold(PhysicalItemType.SPEED_BOOST);
        engine.useItem(g, p, 1000);
        assertThat(p.isSpeedBoosted(2000)).isTrue();
        assertThat(p.isSpeedBoosted(6000)).isFalse();

        // 부스트 중: 50ms 간격으로 이동 가능(기본 100ms보다 짧음)
        p.setIntent(Direction.RIGHT);
        p.moveTo(5, 5, 1000);
        engine.tickMovement(g, 1060); // 60ms 경과 → 기본(100)이면 불가, 부스트(50)면 가능
        assertThat(p.getX()).isEqualTo(6);
    }

    @Test
    @DisplayName("이동: 의도 방향으로 쿨다운에 맞춰 한 칸씩, 벽은 통과 못 한다")
    void movement() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        engine.startMove(g, p, Direction.RIGHT, 1000); // 즉시 한 칸 → (6,5)
        assertThat(p.getX()).isEqualTo(6);

        engine.tickMovement(g, 1050); // 50ms(<100) → 정지
        assertThat(p.getX()).isEqualTo(6);

        engine.tickMovement(g, 1120); // 120ms 경과 → (7,5)
        assertThat(p.getX()).isEqualTo(7);

        // 우측 벽: x=SIZE-1 에서 RIGHT → 이동 불가
        p.moveTo(SIZE - 1, 5, 0);
        p.setIntent(Direction.RIGHT);
        engine.tickMovement(g, 10_000);
        assertThat(p.getX()).isEqualTo(SIZE - 1);
    }

    @Test
    @DisplayName("아이템 픽업: 슬롯이 비었을 때만 드롭 칸에서 자동 획득한다")
    void pickup() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        g.drops().add(new ItemDrop(6, 5, PhysicalItemType.BOMB));

        engine.startMove(g, p, Direction.RIGHT, 1000); // (6,5)로 이동 → 픽업
        assertThat(p.getHeldItem()).isEqualTo(PhysicalItemType.BOMB);
        assertThat(g.drops()).isEmpty();

        // 슬롯이 찼으면 추가 드롭은 획득하지 않음
        g.drops().add(new ItemDrop(7, 5, PhysicalItemType.CRATER));
        engine.tickMovement(g, 1200); // (7,5)
        assertThat(p.getHeldItem()).isEqualTo(PhysicalItemType.BOMB);
        assertThat(g.drops()).hasSize(1);
    }

    @Test
    @DisplayName("아이템 스폰: 주기 경과 후 빈 칸에 드롭이 생성된다")
    void spawn() {
        PhysicalGame g = newGame();
        engine.maybeSpawnItem(g, 500);  // 주기(1000) 전 → 스폰 없음
        assertThat(g.drops()).isEmpty();

        engine.maybeSpawnItem(g, 2000); // 주기 후 → 스폰
        assertThat(g.drops()).hasSize(1);
        ItemDrop drop = g.drops().get(0);
        assertThat(drop.x()).isBetween(0, SIZE - 1);
        assertThat(drop.y()).isBetween(0, SIZE - 1);
        assertThat(g.board().isPlaceable(drop.x(), drop.y())).isTrue();
    }

    @Test
    @DisplayName("이동: 분화구(붕괴된 칸)는 통과할 수 없다")
    void craterBlocksMovement() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        g.board().crater(6, 5);      // 오른쪽 칸 붕괴
        engine.startMove(g, p, Direction.RIGHT, 1000);
        assertThat(p.getX()).isEqualTo(5); // 막혀서 못 감
    }

    @Test
    @DisplayName("아이템(상대돌 제거): 가장 가까운 상대 돌을 제거, 없으면 아이템 유지")
    void removeStoneItem() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)

        // 제거할 상대 돌이 없으면 슬롯 유지(낭비 방지)
        p.hold(PhysicalItemType.REMOVE_STONE);
        engine.useItem(g, p, 1000);
        assertThat(p.getHeldItem()).isEqualTo(PhysicalItemType.REMOVE_STONE);

        // 백 돌 2개 — 가까운 (5,7)이 제거되고 먼 (12,12)는 남는다
        g.board().place(5, 7, StoneColor.WHITE);
        g.board().place(12, 12, StoneColor.WHITE);
        engine.useItem(g, p, 2000);
        assertThat(g.board().stoneAt(5, 7)).isNull();
        assertThat(g.board().stoneAt(12, 12)).isEqualTo(StoneColor.WHITE);
        assertThat(p.getHeldItem()).isNull();
    }
}
