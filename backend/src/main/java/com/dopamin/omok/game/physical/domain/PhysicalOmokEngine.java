package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.effect.PhysicalItemEffectRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 피지컬 오목의 모든 규칙을 서버 권위로 적용하는 순수 엔진(상태는 {@link PhysicalGame} 가 보유).
 * 이동·착수·파괴·아이템·스폰의 쿨다운/범위/유효성을 전부 여기서 검증한다(클라 입력 신뢰 안 함).
 * 모든 메서드는 세션 락 하에서만 호출된다.
 */
@Component
public class PhysicalOmokEngine {

    private final PhysicalOmokProperties props;
    private final PhysicalItemEffectRegistry effects;
    private final Random random;

    @Autowired
    public PhysicalOmokEngine(PhysicalOmokProperties props, PhysicalItemEffectRegistry effects) {
        this(props, effects, new Random());
    }

    /** 테스트에서 시드 가능한 Random 주입용. */
    PhysicalOmokEngine(PhysicalOmokProperties props, PhysicalItemEffectRegistry effects, Random random) {
        this.props = props;
        this.effects = effects;
        this.random = random;
    }

    // ===================== 입력 적용 =====================

    /** 이동 의도 설정 + 즉시 한 칸(반응성). 키를 누르고 있는 동안 틱이 이어서 진행한다. */
    public void startMove(PhysicalGame game, PhysicalPlayer player, Direction dir, long now) {
        if (dir == null) return;
        player.setIntent(dir);
        tryStep(game, player, now);
    }

    public void stopMove(PhysicalPlayer player) {
        player.clearIntent();
    }

    /**
     * 착수: 쿨다운 경과 + 현재 칸이 착수 가능일 때만. 완성된 오목은 즉시 점수가 되지 않고
     * winSettleMs 동안 '게이지'가 차야 확정된다(충전 관리는 매 틱 {@link #tickPendingLines} 가 보드를 보고 처리).
     * @return 이번 착수로 오목이 완성됐는지(사운드/판정 보조용).
     */
    public boolean place(PhysicalGame game, PhysicalPlayer player, long now) {
        if (now - player.getLastPlaceAt() < props.placeCooldownMs()) return false;
        int x = player.getX(), y = player.getY();
        if (!game.board().isPlaceable(x, y)) return false;
        game.board().place(x, y, player.getColor());
        player.markPlaced(now);
        return game.board().checkWin(x, y, game.winCount());
    }

    /**
     * 충전 중인 완성 라인들을 매 틱 갱신·정산한다.
     * 1) 현재 보드의 완성 라인 전체를 색별로 다시 수집해 충전 상태와 대조한다
     *    — 5목을 6·7목으로 늘려도, 6목의 끝이 끊겨 5목만 남아도 같은 줄로 보고 게이지를 이어 충전한다
     *    (이게 "기존 오목 이상에서 끊기면 감지 못함" 문제를 없앤다). 완전히 끊겨 사라진 줄은 자동으로 빠진다.
     * 2) 충전이 끝난(lockAt 경과) 줄의 돌만 제거하며 1점씩 득점한다(보드는 유지 — 누적 점수전).
     *    같은 틱에 여러 줄이 동시에 완충될 수 있어 '선별 → 제거'를 2단계로 분리한다. targetScore 도달 시 승리.
     * @return 이번 틱에 득점(라인 제거)이 발생했는지 — 호출 측이 보드 변화를 리플레이에 기록하는 데 쓴다.
     */
    public boolean tickPendingLines(PhysicalGame game, long now) {
        // 1) 현재 완성 라인 전체 재수집 → 충전 상태 갱신(연장/일부 끊김에 강함)
        List<PendingLine> current = new ArrayList<>();
        for (StoneColor color : StoneColor.values()) {
            for (List<int[]> cells : game.board().findAllWinningLines(color, game.winCount())) {
                current.add(new PendingLine(color, cells, 0L)); // lockAt 은 reconcile 이 채움
            }
        }
        game.reconcilePendingLines(current, now + props.winSettleMs());

        // 2) 완충된 줄 선별
        List<PendingLine> toScore = new ArrayList<>();
        Iterator<PendingLine> it = game.pendingLines().iterator();
        while (it.hasNext()) {
            PendingLine pl = it.next();
            if (now >= pl.lockAt()) { toScore.add(pl); it.remove(); }
        }
        if (toScore.isEmpty()) return false;

        // 3) 선별된 줄의 돌 제거 + 득점(제거가 같은 틱 다른 줄 판정을 깨지 않도록 분리됨)
        List<PendingLine> cleared = new ArrayList<>();
        for (PendingLine pl : toScore) {
            for (int[] cell : pl.cells()) {
                game.board().removeStone(cell[0], cell[1]);
            }
            cleared.add(pl);
            int score = game.addScore(pl.color());
            if (score >= game.targetScore()) {
                game.finish(pl.color()); // 승리 — 남은 충전 줄은 의미 없음(finish 가 비움)
                break;
            }
        }
        game.recordClearedLines(cleared); // 클라 특수효과(줄별 파괴 연출)용 1회성 신호
        return true;
    }

    /** 파괴: 2초 쿨다운 경과 + 현재 칸에 '상대 돌'이 있을 때만 제거한다. */
    public void destroy(PhysicalGame game, PhysicalPlayer player, long now) {
        if (now - player.getLastDestroyAt() < props.destroyCooldownMs()) return;
        int x = player.getX(), y = player.getY();
        StoneColor target = game.board().stoneAt(x, y);
        if (target == null || target == player.getColor()) return; // 내 돌/빈칸은 파괴 불가
        game.board().removeStone(x, y);
        player.markDestroyed(now);
        // 끊긴 충전 줄 정리는 매 틱 tickPendingLines 의 재수집(reconcile)이 담당한다(다음 틱 ≤tick 간격).
    }

    /** 보유 아이템 사용. 효과가 실제로 적용됐을 때만 슬롯을 비운다(예: 제거할 상대 돌이 없으면 유지). */
    public void useItem(PhysicalGame game, PhysicalPlayer player, long now) {
        PhysicalItemType item = player.getHeldItem();
        if (item == null) return;
        if (effects.apply(item, game, player, now)) {
            player.clearItem();
        }
    }

    // ===================== 틱 루프 =====================

    /** 의도 방향이 있는 플레이어를 쿨다운에 맞춰 한 칸씩 전진시킨다. */
    public void tickMovement(PhysicalGame game, long now) {
        for (PhysicalPlayer player : game.players()) {
            if (player.getIntent() != null) {
                tryStep(game, player, now);
            }
        }
    }

    private void tryStep(PhysicalGame game, PhysicalPlayer player, long now) {
        Direction dir = player.getIntent();
        if (dir == null) return;
        long cooldown = player.isSpeedBoosted(now)
                ? props.speedBoostMoveCooldownMs()
                : props.moveCooldownMs();
        if (now - player.getLastMoveAt() < cooldown) return;
        int nx = player.getX() + dir.dx, ny = player.getY() + dir.dy;
        // 벽(범위 밖) 또는 분화구(붕괴된 칸)는 통과 불가 — 정지(쿨다운 미갱신, 다음 틱 재시도)
        if (!game.board().inBounds(nx, ny) || game.board().isBlocked(nx, ny)) return;
        player.moveTo(nx, ny, now);
        tryPickup(game, player);
    }

    private void tryPickup(PhysicalGame game, PhysicalPlayer player) {
        if (player.getHeldItem() != null) return; // 슬롯이 비었을 때만 자동 획득
        Iterator<ItemDrop> it = game.drops().iterator();
        while (it.hasNext()) {
            ItemDrop drop = it.next();
            if (drop.x() == player.getX() && drop.y() == player.getY()) {
                player.hold(drop.type());
                it.remove();
                return;
            }
        }
    }

    /** 스폰 주기마다 필드 아이템이 한도 미만이면 빈 칸에 가중 랜덤 종류로 1개 생성한다. */
    public void maybeSpawnItem(PhysicalGame game, long now) {
        if (now - game.lastItemSpawnAt() < props.itemSpawnIntervalMs()) return;
        game.setLastItemSpawnAt(now);
        if (game.drops().size() >= props.maxItemsOnField()) return;
        PhysicalItemType type = pickWeightedType();
        if (type == null) return;
        int[] cell = randomSpawnCell(game);
        if (cell == null) return;
        game.drops().add(new ItemDrop(cell[0], cell[1], type));
    }

    private PhysicalItemType pickWeightedType() {
        int total = 0;
        for (Map.Entry<PhysicalItemType, Integer> e : props.itemWeights().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) total += e.getValue();
        }
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Map.Entry<PhysicalItemType, Integer> e : props.itemWeights().entrySet()) {
            int w = e.getValue() == null ? 0 : e.getValue();
            if (w <= 0) continue;
            roll -= w;
            if (roll < 0) return e.getKey();
        }
        return null;
    }

    /** 돌/분화구/기존 드롭이 없는 빈 칸을 랜덤으로 고른다(여러 번 시도 후 실패 시 null). */
    private int[] randomSpawnCell(PhysicalGame game) {
        int size = game.board().size();
        for (int attempt = 0; attempt < 60; attempt++) {
            int x = random.nextInt(size), y = random.nextInt(size);
            if (!game.board().isPlaceable(x, y)) continue; // 돌/분화구 없는 칸
            if (hasDropAt(game, x, y)) continue;
            return new int[]{x, y};
        }
        return null;
    }

    private boolean hasDropAt(PhysicalGame game, int x, int y) {
        for (ItemDrop d : game.drops()) {
            if (d.x() == x && d.y() == y) return true;
        }
        return false;
    }
}
