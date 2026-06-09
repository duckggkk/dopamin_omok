package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.StoneColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 피지컬 오목 보드(N×N). 각 칸 상태: 빈칸 | 돌(흑/백) | 분화구(BLOCKED, 영구 착수 불가).
 * 돌·분화구는 캐릭터 이동을 막지 않는다(상대 돌 위에 서서 파괴해야 하므로).
 * 좌표계: x=열, y=행. 모든 변형은 세션 락 하에서만 호출되므로 스레드 안전성은 호출 측이 보장한다.
 */
public class PhysicalBoard {

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private final int size;
    private final StoneColor[][] stones; // [y][x], null=빈칸
    private final boolean[][] blocked;   // [y][x], true=분화구

    public PhysicalBoard(int size) {
        this.size = size;
        this.stones = new StoneColor[size][size];
        this.blocked = new boolean[size][size];
    }

    public int size() {
        return size;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    public StoneColor stoneAt(int x, int y) {
        return inBounds(x, y) ? stones[y][x] : null;
    }

    public boolean isBlocked(int x, int y) {
        return inBounds(x, y) && blocked[y][x];
    }

    /** 착수 가능: 칸 안 + 돌 없음 + 분화구 아님. */
    public boolean isPlaceable(int x, int y) {
        return inBounds(x, y) && stones[y][x] == null && !blocked[y][x];
    }

    public void place(int x, int y, StoneColor color) {
        if (inBounds(x, y)) stones[y][x] = color;
    }

    public void removeStone(int x, int y) {
        if (inBounds(x, y)) stones[y][x] = null;
    }

    /** 현재 칸 돌 제거 + 영구 착수 불가(분화구). */
    public void crater(int x, int y) {
        if (!inBounds(x, y)) return;
        stones[y][x] = null;
        blocked[y][x] = true;
    }

    /** (cx,cy) 중심 3×3 내 모든 돌(색 무관) 제거. 분화구는 유지. */
    public void bombArea(int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                removeStone(cx + dx, cy + dy);
            }
        }
    }

    /** 보드 전체에서 {@code color} 의 winCount 연속이 하나라도 있으면 true(승리 확정 재검증용). */
    public boolean hasLine(StoneColor color, int winCount) {
        return !findWinningLine(color, winCount).isEmpty();
    }

    /**
     * 보드 전체에서 {@code color} 의 winCount 이상 연속 라인을 찾아 그 칸 좌표들을 반환한다(없으면 빈 리스트).
     * 5목 확정 대기(settle) 동안 클라이언트가 '연결된 오목'을 강조 표시하는 데 쓴다.
     * 6목 이상이면 양 끝까지 모두 포함한다. 좌표는 {@code [x, y]} 형식.
     */
    public List<int[]> findWinningLine(StoneColor color, int winCount) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (stones[y][x] != color) continue;
                for (int[] d : DIRS) {
                    int back = run(x, y, -d[0], -d[1], color);
                    int fwd = run(x, y, d[0], d[1], color);
                    int len = 1 + back + fwd;
                    if (len < winCount) continue;
                    List<int[]> line = new ArrayList<>(len);
                    int sx = x - d[0] * back, sy = y - d[1] * back;
                    for (int i = 0; i < len; i++) {
                        line.add(new int[]{sx + d[0] * i, sy + d[1] * i});
                    }
                    return line;
                }
            }
        }
        return List.of();
    }

    /** (x,y)에 놓인 돌 기준 4방향 연속 길이가 winCount 이상이면 승리. */
    public boolean checkWin(int x, int y, int winCount) {
        StoneColor color = stoneAt(x, y);
        if (color == null) return false;
        for (int[] d : DIRS) {
            int count = 1 + run(x, y, d[0], d[1], color) + run(x, y, -d[0], -d[1], color);
            if (count >= winCount) return true;
        }
        return false;
    }

    private int run(int x, int y, int dx, int dy, StoneColor color) {
        int c = 0, nx = x + dx, ny = y + dy;
        while (inBounds(nx, ny) && stones[ny][nx] == color) {
            c++;
            nx += dx;
            ny += dy;
        }
        return c;
    }

    /** 스냅샷용 인코딩: 0=빈칸, 1=흑, 2=백, 3=분화구. [y][x] 순. */
    public int[][] encode() {
        int[][] cells = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (blocked[y][x]) cells[y][x] = 3;
                else if (stones[y][x] == StoneColor.BLACK) cells[y][x] = 1;
                else if (stones[y][x] == StoneColor.WHITE) cells[y][x] = 2;
                else cells[y][x] = 0;
            }
        }
        return cells;
    }
}
