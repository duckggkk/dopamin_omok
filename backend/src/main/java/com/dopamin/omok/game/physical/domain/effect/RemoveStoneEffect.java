package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.domain.PhysicalBoard;
import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import org.springframework.stereotype.Component;

/**
 * 나와 가장 가까운 상대 돌 하나를 어디서든 제거한다(게임 시작 시 1개 보유).
 * 제거할 상대 돌이 없으면 false 를 반환해 아이템을 소모하지 않는다(시작 직후 빈 보드에서 낭비 방지).
 * 스폰 가중치에 없어 필드에서는 나오지 않는다(시작 보유 전용).
 */
@Component
public class RemoveStoneEffect implements PhysicalItemEffect {

    @Override
    public PhysicalItemType type() {
        return PhysicalItemType.REMOVE_STONE;
    }

    @Override
    public boolean apply(PhysicalGame game, PhysicalPlayer player, long now) {
        StoneColor opponent = player.getColor() == StoneColor.BLACK ? StoneColor.WHITE : StoneColor.BLACK;
        PhysicalBoard board = game.board();
        int size = board.size();

        int bestDist = Integer.MAX_VALUE, bx = -1, by = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (board.stoneAt(x, y) != opponent) continue;
                int dist = Math.abs(x - player.getX()) + Math.abs(y - player.getY());
                if (dist < bestDist) {
                    bestDist = dist;
                    bx = x;
                    by = y;
                }
            }
        }
        if (bx < 0) return false; // 제거할 상대 돌 없음 → 아이템 유지
        board.removeStone(bx, by);
        return true;
    }
}
