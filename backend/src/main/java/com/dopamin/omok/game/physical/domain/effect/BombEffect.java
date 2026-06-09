package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import org.springframework.stereotype.Component;

/** 내 주변 3×3 안의 모든 돌(내 돌+상대 돌)을 제거한다(역전 카드). */
@Component
public class BombEffect implements PhysicalItemEffect {

    @Override
    public PhysicalItemType type() {
        return PhysicalItemType.BOMB;
    }

    @Override
    public boolean apply(PhysicalGame game, PhysicalPlayer player, long now) {
        game.board().bombArea(player.getX(), player.getY());
        return true;
    }
}
