package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.domain.Direction;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 휴리스틱 봇 두뇌 단위 테스트. 정책이 순수 함수(관측→행동)라 엔진/세션 없이 보드만 차려 검증한다 —
 * 이게 추상화의 이점이다(딥러닝 정책도 같은 방식으로 같은 케이스를 통과시키면 교체 가능).
 *
 * 셀 인코딩: 0=빈칸, 1=흑, 2=백, 3=분화구.
 */
class HeuristicPhysicalBotPolicyTest {

    private static final int SIZE = 14;
    private static final int WIN = 5;

    private final HeuristicPhysicalBotPolicy policy = new HeuristicPhysicalBotPolicy();

    private int[][] emptyBoard() {
        return new int[SIZE][SIZE];
    }

    private BotObservation obs(int[][] cells, StoneColor self, int sx, int sy,
                               boolean canPlace, boolean canDestroy) {
        return new BotObservation(SIZE, WIN, cells, self, sx, sy, 0, 0,
                canPlace, canDestroy, false, null, List.of());
    }

    @Test
    @DisplayName("완성 자리에 서서 둘 수 있으면 착수해 5목을 완성한다")
    void placesWinningStone() {
        int[][] cells = emptyBoard();
        // 백 4개 가로: x=5..8, y=5 → 완성 칸은 (9,5) 또는 (4,5)
        for (int x = 5; x <= 8; x++) cells[5][x] = 2;

        BotAction action = policy.decide(obs(cells, StoneColor.WHITE, 9, 5, true, false));

        assertThat(action.kind()).isEqualTo(BotAction.Kind.PLACE);
    }

    @Test
    @DisplayName("상대의 4목(한 수면 패) 완성 칸으로 이동해 막으러 간다")
    void movesToBlockOpponentThreat() {
        int[][] cells = emptyBoard();
        // 흑 4개 가로: x=5..8, y=5 → 완성 칸 (9,5). 봇은 그 아래 (9,7)에 위치.
        for (int x = 5; x <= 8; x++) cells[5][x] = 1;

        BotAction action = policy.decide(obs(cells, StoneColor.WHITE, 9, 7, true, false));

        assertThat(action.kind()).isEqualTo(BotAction.Kind.MOVE);
        assertThat(action.direction()).isEqualTo(Direction.UP); // (9,7)→(9,5) 위로
    }

    @Test
    @DisplayName("상대 강선의 돌 위에 서 있고 파괴 가능하면 파괴한다")
    void destroysKeystoneWhenStandingOnIt() {
        int[][] cells = emptyBoard();
        // 흑 4목, 양 끝(4,5)(9,5)은 분화구라 '착수로 막기' 불가 → 파괴가 최선.
        for (int x = 5; x <= 8; x++) cells[5][x] = 1;
        cells[5][4] = 3;
        cells[5][9] = 3;

        // 봇(백)이 강선 첫 돌 (5,5) 위에 서 있고 파괴 쿨다운 해제.
        BotAction action = policy.decide(obs(cells, StoneColor.WHITE, 5, 5, true, true));

        assertThat(action.kind()).isEqualTo(BotAction.Kind.DESTROY);
    }

    @Test
    @DisplayName("자기 발밑이 분화구여도 갇히지 않고 길을 찾아 행동한다(분화구 끼임 회귀 방지)")
    void escapesWhenStandingOnCrater() {
        int[][] cells = emptyBoard();
        // 봇(백)이 분화구 위에 서 있다(예: CRATER 아이템을 발밑에 사용한 직후) — 예전엔 BFS 가 전부 -1 이라 영영 IDLE.
        cells[3][3] = 3;
        // 흑 4목 → 도달 가능한 목표(막기/파괴)가 존재 → 봇은 분화구에서 나와 행동해야 한다.
        for (int x = 5; x <= 8; x++) cells[5][x] = 1;

        BotAction action = policy.decide(obs(cells, StoneColor.WHITE, 3, 3, true, true));

        assertThat(action.kind()).isNotEqualTo(BotAction.Kind.IDLE);
    }

    @Test
    @DisplayName("이동 부스트를 들고 있으면 즉시 사용한다")
    void usesSpeedBoostImmediately() {
        int[][] cells = emptyBoard();
        cells[5][5] = 2; // 백 돌 하나(후보 칸 존재용)

        BotObservation o = new BotObservation(SIZE, WIN, cells, StoneColor.WHITE, 7, 7, 0, 0,
                true, false, false, PhysicalItemType.SPEED_BOOST, List.of());

        assertThat(policy.decide(o).kind()).isEqualTo(BotAction.Kind.USE_ITEM);
    }
}
