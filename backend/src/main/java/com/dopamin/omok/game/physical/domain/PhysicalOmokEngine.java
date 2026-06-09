package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.effect.PhysicalItemEffectRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
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
     * 착수: 쿨다운 경과 + 현재 칸이 착수 가능일 때만. 5목 완성 시 즉시 끝내지 않고
     * '승리 확정 대기'를 시작한다(settle) — winSettleMs 동안 유지돼야 확정. @return 5목을 새로 완성했는지.
     */
    public boolean place(PhysicalGame game, PhysicalPlayer player, long now) {
        if (now - player.getLastPlaceAt() < props.placeCooldownMs()) return false;
        int x = player.getX(), y = player.getY();
        if (!game.board().isPlaceable(x, y)) return false;
        game.board().place(x, y, player.getColor());
        player.markPlaced(now);
        if (game.board().checkWin(x, y, game.winCount())) {
            game.startPendingWin(player.getColor(), now + props.winSettleMs());
            return true;
        }
        return false;
    }

    /**
     * 승리 확정 대기를 처리한다(매 틱). lockAt 경과 시 해당 색의 5목이 '여전히' 존재하면 승리 확정,
     * 그 사이 상대가 끊었으면(라인 소멸) 무효 처리한다. 이게 오프닝 직선 러시를 막는 핵심 반격창이다.
     */
    public void settlePendingWin(PhysicalGame game, long now) {
        if (!game.hasPendingWin() || now < game.pendingWinLockAt()) return;
        StoneColor color = game.pendingWinColor();
        if (game.board().hasLine(color, game.winCount())) {
            game.finish(color);
        } else {
            game.clearPendingWin();
        }
    }

    /** 파괴: 2초 쿨다운 경과 + 현재 칸에 '상대 돌'이 있을 때만 제거한다. */
    public void destroy(PhysicalGame game, PhysicalPlayer player, long now) {
        if (now - player.getLastDestroyAt() < props.destroyCooldownMs()) return;
        int x = player.getX(), y = player.getY();
        StoneColor target = game.board().stoneAt(x, y);
        if (target == null || target == player.getColor()) return; // 내 돌/빈칸은 파괴 불가
        game.board().removeStone(x, y);
        player.markDestroyed(now);
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
