package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.physical.config.PhysicalOmokProperties;
import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 일정 시간 이동 쿨다운을 단축한다(흔한 아이템). */
@Component
@RequiredArgsConstructor
public class SpeedBoostEffect implements PhysicalItemEffect {

    private final PhysicalOmokProperties props;

    @Override
    public PhysicalItemType type() {
        return PhysicalItemType.SPEED_BOOST;
    }

    @Override
    public boolean apply(PhysicalGame game, PhysicalPlayer player, long now) {
        player.startSpeedBoost(now + props.speedBoostDurationMs());
        return true;
    }
}
