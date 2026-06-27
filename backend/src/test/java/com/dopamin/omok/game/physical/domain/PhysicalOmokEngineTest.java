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
        // boardSize=14, winCount=5, targetScore=3, move=100, place=300, destroy=2000, winSettle=400,
        // countdown=3000, tick=60, spawnInterval=1000, maxItems=3, boostDuration=5000, boostMoveCd=50, training=false, inputBuffer=130, moveQueueMax=6
        return new PhysicalOmokProperties(SIZE, 5, 3, 100, 300, 2000, 400, 3000, 60, 1000, 3, 5000, 50, weights, false, 130, 6);
    }

    private PhysicalGame newGame() {
        return newGame(3);
    }

    private PhysicalGame newGame(int targetScore) {
        PhysicalGame game = new PhysicalGame("ROOM", 1L, SIZE, 5, targetScore, 0L, 0L);
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
    @DisplayName("이동 버퍼 큐: 방향을 빠르게 번갈아 입력해도 마지막 것만 남지 않고 순서대로 모두 소화한다")
    void rapidAlternatingDirectionsAreQueuedInOrder() {
        PhysicalGame g = newGame();
        PhysicalPlayer p = black(g); // (5,5)
        int x0 = p.getX(), y0 = p.getY();

        // 위·오른쪽을 쿨다운(100ms)보다 빠르게 번갈아 탭(같은 시각) — 예전 단일 슬롯 버퍼는 마지막 방향만 남아 '오른쪽만' 갔다.
        long t = 1000;
        for (Direction dir : new Direction[]{Direction.UP, Direction.RIGHT, Direction.UP, Direction.RIGHT}) {
            engine.startMove(g, p, dir, t);
            engine.stopMove(p); // 눌렀다 바로 뗌(빠른 탭)
        }

        // 틱을 쿨다운 간격으로 진행해 큐를 소진
        for (int i = 0; i < 6; i++) { t += 100; engine.tickMovement(g, t); }

        // 4번 입력(UP·RIGHT·UP·RIGHT)이 모두 반영 → 위로 2칸, 오른쪽으로 2칸(한 방향만 가면 실패).
        assertThat(p.getX()).isEqualTo(x0 + 2);
        assertThat(p.getY()).isEqualTo(y0 - 2);
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
    @DisplayName("오목 완성: 게이지가 차면 1점 + 완성 라인만 제거되고 보드는 유지된 채 게임이 계속된다")
    void scoreRemovesLineAndGameContinues() {
        PhysicalGame g = newGame(3);
        for (int x = 0; x < 4; x++) g.board().place(x, 0, StoneColor.BLACK);
        g.board().place(7, 7, StoneColor.BLACK); // 라인과 무관한 돌 — 보드 유지 확인용
        PhysicalPlayer p = black(g);
        p.moveTo(4, 0, 0);

        boolean formed = engine.place(g, p, 1000);
        assertThat(formed).isTrue();
        engine.tickPendingLines(g, 1000); // 틱 재수집 → 충전 시작
        assertThat(g.status()).isEqualTo(GameStatus.IN_PROGRESS); // 충전 전엔 미확정
        assertThat(g.pendingLines()).hasSize(1);
        assertThat(g.pendingLines().get(0).color()).isEqualTo(StoneColor.BLACK);

        boolean scored = engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1); // 충전 완료 → 1점
        assertThat(scored).isTrue();
        assertThat(g.status()).isEqualTo(GameStatus.IN_PROGRESS);      // 1/3점 → 게임 계속
        assertThat(g.pendingLines()).isEmpty();
        assertThat(g.scoreOf(StoneColor.BLACK)).isEqualTo(1);
        for (int x = 0; x <= 4; x++) assertThat(g.board().stoneAt(x, 0)).isNull(); // 완성 라인만 제거
        assertThat(g.board().stoneAt(7, 7)).isEqualTo(StoneColor.BLACK);           // 나머지 보드는 유지
        assertThat(g.lastClearedLines()).hasSize(1);                               // 특수효과 신호
        assertThat(g.scoreEventSeq()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 착수가 두 줄(십자)을 동시에 완성하면 각각 1점씩, 두 줄 모두 제거된다")
    void forkScoresBothLines() {
        PhysicalGame g = newGame(3);
        // (5,5)를 지나는 가로/세로 각각 4목을 미리 깔아두고, (5,5) 착수로 동시에 완성
        for (int x = 2; x <= 6; x++) if (x != 5) g.board().place(x, 5, StoneColor.BLACK); // 가로
        for (int y = 2; y <= 6; y++) if (y != 5) g.board().place(5, y, StoneColor.BLACK); // 세로
        PhysicalPlayer p = black(g);
        p.moveTo(5, 5, 0);

        engine.place(g, p, 1000);
        engine.tickPendingLines(g, 1000); // 재수집 → 가로 + 세로 (피벗 1칸만 공유 → 별개 줄)
        assertThat(g.pendingLines()).hasSize(2);

        boolean scored = engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1);
        assertThat(scored).isTrue();
        assertThat(g.scoreOf(StoneColor.BLACK)).isEqualTo(2);       // 두 줄 → 2점
        assertThat(g.lastClearedLines()).hasSize(2);
        for (int x = 2; x <= 6; x++) assertThat(g.board().stoneAt(x, 5)).isNull(); // 가로 제거
        for (int y = 2; y <= 6; y++) assertThat(g.board().stoneAt(5, y)).isNull(); // 세로 제거
    }

    @Test
    @DisplayName("연장(5→6목)해도 게이지가 리셋되지 않고 처음 시작한 타이머로 확정된다")
    void extendingDoesNotResetGauge() {
        PhysicalGame g = newGame(3);
        for (int y = 0; y < 5; y++) g.board().place(0, y, StoneColor.BLACK); // 세로 5목
        engine.tickPendingLines(g, 1000); // 충전 시작 → lockAt = 1000 + settle
        assertThat(g.pendingLines()).hasSize(1);

        g.board().place(0, 5, StoneColor.BLACK); // 충전 중 6목으로 연장
        boolean mid = engine.tickPendingLines(g, 1000 + props.winSettleMs() / 2); // 같은 줄 → 타이머 유지
        assertThat(mid).isFalse();
        assertThat(g.pendingLines()).hasSize(1);

        boolean scored = engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1); // 처음 타이머 기준 확정
        assertThat(scored).isTrue();
        assertThat(g.scoreOf(StoneColor.BLACK)).isEqualTo(1);
        for (int y = 0; y <= 5; y++) assertThat(g.board().stoneAt(0, y)).isNull(); // 6목 전부 제거
    }

    @Test
    @DisplayName("6목의 끝이 끊겨 5목만 남아도 충전이 이어져 득점된다(기존 오목 이상 끊김 감지)")
    void cutEndOfSixKeepsChargingFive() {
        PhysicalGame g = newGame(3);
        for (int y = 0; y < 6; y++) g.board().place(0, y, StoneColor.BLACK); // 세로 6목
        engine.tickPendingLines(g, 1000); // 충전 시작 → lockAt = 1000 + settle
        assertThat(g.pendingLines()).hasSize(1);

        g.board().removeStone(0, 5); // 끝 1개 끊김 → (0,0)~(0,4) 5목은 여전히 온전
        boolean mid = engine.tickPendingLines(g, 1100); // 같은 줄(축소)로 보고 타이머 유지
        assertThat(mid).isFalse();
        assertThat(g.pendingLines()).hasSize(1);

        boolean scored = engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1); // 남은 5목으로 확정
        assertThat(scored).isTrue();
        assertThat(g.scoreOf(StoneColor.BLACK)).isEqualTo(1);
        for (int y = 0; y <= 4; y++) assertThat(g.board().stoneAt(0, y)).isNull();
    }

    @Test
    @DisplayName("targetScore 도달: 마지막 오목을 완성하면 승리 확정된다")
    void reachingTargetScoreWins() {
        PhysicalGame g = newGame(1); // 1점이면 즉시 승리
        for (int x = 0; x < 4; x++) g.board().place(x, 0, StoneColor.BLACK);
        PhysicalPlayer p = black(g);
        p.moveTo(4, 0, 0);
        engine.place(g, p, 1000);
        engine.tickPendingLines(g, 1000); // 충전 시작

        engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1); // 충전 완료 → 1점 = 목표 도달
        assertThat(g.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(g.winnerColor()).isEqualTo(StoneColor.BLACK);
        assertThat(g.winnerUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("충전 동안 상대가 라인을 끊으면 게이지가 무효 처리된다(오프닝 러시 방지)")
    void pendingLineCancelledIfBroken() {
        PhysicalGame g = newGame();
        for (int x = 0; x < 4; x++) g.board().place(x, 0, StoneColor.BLACK);
        PhysicalPlayer p = black(g);
        p.moveTo(4, 0, 0);
        engine.place(g, p, 1000); // 5목
        engine.tickPendingLines(g, 1000); // 충전 시작
        assertThat(g.pendingLines()).hasSize(1);

        g.board().removeStone(2, 0); // 상대가 가운데 돌 제거 → 라인 끊김(5목 사라짐)
        boolean scored = engine.tickPendingLines(g, 1000 + props.winSettleMs() + 1);

        assertThat(scored).isFalse();
        assertThat(g.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(g.pendingLines()).isEmpty();
        assertThat(g.scoreOf(StoneColor.BLACK)).isZero(); // 끊겼으면 무득점
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
