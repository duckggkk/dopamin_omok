package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import org.springframework.stereotype.Component;

/** 현재 칸의 돌을 파괴하고 그 칸을 영구 착수 불가(분화구)로 만든다. */
@Component
public class CraterEffect implements PhysicalItemEffect {

    @Override
    public PhysicalItemType type() {
        return PhysicalItemType.CRATER;
    }

    @Override
    public boolean apply(PhysicalGame game, PhysicalPlayer player, long now) {
        game.board().crater(player.getX(), player.getY());
        return true;
    }
}
