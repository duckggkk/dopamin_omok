package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.physical.domain.Direction;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 규칙 기반(휴리스틱) 피지컬 봇 두뇌 — "유틸리티 AI".
 *
 * 매 틱 후보 목표마다 점수를 매겨 "점수 ÷ 도달거리"가 가장 높은 목표를 고르고, 그쪽으로 한 칸 이동하거나
 * (이미 그 칸이면) 착수/파괴한다. 착수 자리·차단 자리·파괴 대상은 모두 "칸 점수 매기기"로 환원되므로
 * 클래식 오목 휴리스틱과 같은 원리다. 실시간 요소(목표까지 걸어가는 시간)는 거리 패널티로 처리한다.
 *
 * 한계(의도적 단순화, 나중에 딥러닝으로 천장을 올릴 부분):
 *  - 모양 점수는 '연속'만 본다(빈칸 끼운 끊긴 패턴 과소평가).
 *  - 아이템 운용은 최소 규칙(부스트는 바로, 폭탄/분화구는 상대 위협 근처에서)만.
 *  - 상대 이동 예측·페인트 같은 고차원 수는 없음.
 */
@Component
public class HeuristicPhysicalBotPolicy implements PhysicalBotPolicy {

    private static final int CRATER = 3;
    private static final int[][] LINE_DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    // 목표 종류 — 도달 후 무엇을 할지.
    private enum GoalType { PLACE, DESTROY, ITEM }

    private record Goal(GoalType type, int x, int y, double value) {}

    @Override
    public BotAction decide(BotObservation obs) {
        int[][] cells = obs.cells();
        int self = obs.selfCode();
        int opp = obs.oppCode();
        int winCount = obs.winCount();
        int sx = obs.selfX(), sy = obs.selfY();

        // 0) 아이템 사용 — 간단 규칙(이동/착수 판단보다 먼저).
        BotAction itemAction = maybeUseItem(obs, cells, opp, winCount);
        if (itemAction != null) return itemAction;

        // 1) 나에서 모든 칸까지의 이동 거리(분화구 우회 BFS). 목표별 '도달거리' 패널티에 쓴다.
        int[][] distFromSelf = bfsDistances(cells, sx, sy);

        // 2) 후보 목표 모으기.
        List<Goal> goals = new ArrayList<>();

        // 2-a) 착수/차단 목표: 빈 칸 중 (내 공격 + 상대 위협 차단)이 가장 큰 칸.
        Goal placeGoal = bestPlaceCell(cells, self, opp, winCount, distFromSelf);
        if (placeGoal != null) goals.add(placeGoal);

        // 2-b) 파괴 목표: 상대의 가장 강한 줄의 핵심 돌(그 위에 서서 부순다).
        Goal destroyGoal = bestDestroyTarget(cells, opp, winCount, distFromSelf);
        if (destroyGoal != null) goals.add(destroyGoal);

        // 2-c) 아이템 줍기 목표: 슬롯이 비었고 필드에 아이템이 있으면 가장 가까운 것.
        if (obs.heldItem() == null) {
            Goal itemGoal = nearestItem(obs, distFromSelf);
            if (itemGoal != null) goals.add(itemGoal);
        }

        if (goals.isEmpty()) return BotAction.idle();

        // 3) 유틸리티 최대 목표 선택.
        Goal best = goals.get(0);
        for (Goal g : goals) {
            if (g.value() > best.value()) best = g;
        }

        // 4) 이미 목표 칸이면 행동, 아니면 한 칸 다가가기.
        if (sx == best.x() && sy == best.y()) {
            return switch (best.type()) {
                case PLACE -> (obs.canPlace() && cells[best.y()][best.x()] == 0)
                        ? BotAction.place() : BotAction.idle();
                case DESTROY -> (obs.canDestroy() && cells[best.y()][best.x()] == opp)
                        ? BotAction.destroy() : BotAction.idle();
                case ITEM -> BotAction.idle(); // 도착하면 엔진이 자동 획득
            };
        }
        Direction step = stepToward(cells, sx, sy, best.x(), best.y());
        return step != null ? BotAction.move(step) : BotAction.idle();
    }

    // ===================== 아이템 =====================

    private BotAction maybeUseItem(BotObservation obs, int[][] cells, int opp, int winCount) {
        PhysicalItemType held = obs.heldItem();
        if (held == null) return null;

        // 이동 부스트: 들고 있고 아직 안 받는 중이면 즉시 사용(언제나 이득).
        if (held == PhysicalItemType.SPEED_BOOST) {
            return obs.speedBoosted() ? null : BotAction.useItem();
        }

        // 폭탄/분화구: 상대가 강한 줄(부족분 2 이하)을 만들었고, 내가 그 줄의 돌 바로 옆/위면 사용.
        int[] strong = strongestOppStone(cells, opp, winCount);
        if (strong == null) return null;
        int len = strong[2];
        if (len < winCount - 2) return null; // 아직 급하지 않음
        int manhattan = Math.abs(obs.selfX() - strong[0]) + Math.abs(obs.selfY() - strong[1]);
        return manhattan <= 1 ? BotAction.useItem() : null;
    }

    // ===================== 착수/차단 자리 =====================

    private Goal bestPlaceCell(int[][] cells, int self, int opp, int winCount, int[][] distFromSelf) {
        int size = cells.length;
        boolean anyStone = false;
        Goal best = null;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] == 1 || cells[y][x] == 2) anyStone = true;
                if (cells[y][x] != 0) continue;          // 빈 칸만
                if (!nearStone(cells, x, y)) continue;    // 돌 주변만(탐색 축소)
                int dist = distFromSelf[y][x];
                if (dist < 0) continue;                   // 도달 불가(분화구로 막힘)

                double offense = attackAt(cells, x, y, self, winCount);
                double defense = attackAt(cells, x, y, opp, winCount);
                double score = offense + defense * 0.95;  // 동점이면 공격 선호
                double util = score / (1 + dist);
                if (best == null || util > best.value()) {
                    best = new Goal(GoalType.PLACE, x, y, util);
                }
            }
        }

        // 빈 보드: 한가운데로.
        if (!anyStone) {
            int mid = size / 2;
            int d = Math.max(0, distFromSelf[mid][mid]);
            return new Goal(GoalType.PLACE, mid, mid, 1000.0 / (1 + d));
        }
        return best;
    }

    // ===================== 파괴 대상 =====================

    private Goal bestDestroyTarget(int[][] cells, int opp, int winCount, int[][] distFromSelf) {
        int[] strong = strongestOppStone(cells, opp, winCount);
        if (strong == null) return null;
        int x = strong[0], y = strong[1], len = strong[2];
        if (len < winCount - 2) return null;             // 3목 미만은 굳이 파괴 안 함
        int dist = distFromSelf[y][x];                   // 돌 위에 서야 파괴(돌은 이동 막지 않음)
        if (dist < 0) return null;
        double value = shapeScore(len, 2, winCount);     // 강한 줄일수록 부수는 가치 큼
        return new Goal(GoalType.DESTROY, x, y, value / (1 + dist));
    }

    /** 상대 돌 중 '가장 긴 연속 줄'에 속한 돌 하나 → {x, y, runLength}. 없으면 null. */
    private int[] strongestOppStone(int[][] cells, int opp, int winCount) {
        int size = cells.length;
        int[] best = null;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] != opp) continue;
                int maxRun = 1;
                for (int[] d : LINE_DIRS) {
                    int run = 1 + run(cells, x, y, d[0], d[1], opp) + run(cells, x, y, -d[0], -d[1], opp);
                    if (run > maxRun) maxRun = run;
                }
                if (best == null || maxRun > best[2]) best = new int[]{x, y, Math.min(maxRun, winCount)};
            }
        }
        return best;
    }

    // ===================== 아이템 줍기 =====================

    private Goal nearestItem(BotObservation obs, int[][] distFromSelf) {
        Goal best = null;
        for (BotObservation.ItemView it : obs.items()) {
            int d = distFromSelf[it.y()][it.x()];
            if (d < 0) continue;
            double util = 300.0 / (1 + d); // 위협 차단/완성보단 낮은 기본 가치
            if (best == null || util > best.value()) {
                best = new Goal(GoalType.ITEM, it.x(), it.y(), util);
            }
        }
        return best;
    }

    // ===================== 모양 점수(휴리스틱 핵심) =====================

    /** (x,y)에 code 를 '놓는다고 가정'했을 때의 공격 점수 = 네 방향 모양 점수 합. */
    private double attackAt(int[][] cells, int x, int y, int code, int winCount) {
        double total = 0;
        for (int[] d : LINE_DIRS) {
            int count = 1;
            int openEnds = 0;
            for (int sign = 1; sign >= -1; sign -= 2) {
                int dx = d[0] * sign, dy = d[1] * sign;
                int nx = x + dx, ny = y + dy;
                while (inBounds(cells, nx, ny) && cells[ny][nx] == code) {
                    count++;
                    nx += dx;
                    ny += dy;
                }
                if (inBounds(cells, nx, ny) && cells[ny][nx] == 0) openEnds++; // 이어갈 수 있는 빈 끝
            }
            total += shapeScore(count, openEnds, winCount);
        }
        return total;
    }

    /** 연속 n개 + 열린 끝 openEnds 모양의 가치. winCount 완성에 가까울수록·열릴수록 급증. */
    private double shapeScore(int n, int openEnds, int winCount) {
        if (n >= winCount) return 1_000_000;
        int need = winCount - n;
        return switch (need) {
            case 1 -> openEnds == 2 ? 100_000 : openEnds == 1 ? 12_000 : 0; // 한 수면 완성
            case 2 -> openEnds == 2 ? 8_000 : openEnds == 1 ? 600 : 0;
            case 3 -> openEnds == 2 ? 400 : openEnds == 1 ? 60 : 0;
            case 4 -> openEnds == 2 ? 40 : openEnds == 1 ? 8 : 0;
            default -> openEnds == 2 ? 5 : 0;
        };
    }

    private int run(int[][] cells, int x, int y, int dx, int dy, int code) {
        int c = 0, nx = x + dx, ny = y + dy;
        while (inBounds(cells, nx, ny) && cells[ny][nx] == code) {
            c++;
            nx += dx;
            ny += dy;
        }
        return c;
    }

    private boolean nearStone(int[][] cells, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (inBounds(cells, nx, ny) && (cells[ny][nx] == 1 || cells[ny][nx] == 2)) return true;
            }
        }
        return false;
    }

    // ===================== 이동(분화구 우회 BFS) =====================

    /**
     * 출발점에서 모든 칸까지의 최단 이동 거리(분화구=벽, 돌은 통과 가능). 도달 불가 = -1.
     *
     * 출발 칸이 분화구여도(예: CRATER 아이템을 자기 발밑에 써서 갇힌 경우) 시작점으로 인정한다.
     * 엔진의 이동 규칙(tryStep)은 '목적지'만 검사하므로 분화구에서 '나오는' 것은 허용되고 '들어가는' 것만 막힌다.
     * 시드하지 않으면 거리장이 전부 -1 이 되어 봇이 목표를 못 만들고 영영 idle 로 갇힌다(과거 '분화구 끼임' 버그).
     */
    private int[][] bfsDistances(int[][] cells, int sx, int sy) {
        int size = cells.length;
        int[][] dist = new int[size][size];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        if (!inBounds(cells, sx, sy)) return dist;

        Deque<int[]> queue = new ArrayDeque<>();
        dist[sy][sx] = 0;
        queue.add(new int[]{sx, sy});
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (Direction dir : Direction.values()) {
                int nx = cur[0] + dir.dx, ny = cur[1] + dir.dy;
                if (!inBounds(cells, nx, ny) || cells[ny][nx] == CRATER) continue;
                if (dist[ny][nx] != -1) continue;
                dist[ny][nx] = dist[cur[1]][cur[0]] + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /** (sx,sy)에서 (tx,ty)로 가는 첫 한 걸음의 방향. 목표 기준 거리장을 만들어 가장 가까워지는 이웃으로. */
    private Direction stepToward(int[][] cells, int sx, int sy, int tx, int ty) {
        int[][] distToTarget = bfsDistances(cells, tx, ty);
        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            int nx = sx + dir.dx, ny = sy + dir.dy;
            if (!inBounds(cells, nx, ny) || cells[ny][nx] == CRATER) continue;
            int d = distToTarget[ny][nx];
            if (d < 0) continue;
            if (d < bestDist) {
                bestDist = d;
                best = dir;
            }
        }
        return best;
    }

    private boolean inBounds(int[][] cells, int x, int y) {
        return x >= 0 && y >= 0 && y < cells.length && x < cells.length;
    }
}
