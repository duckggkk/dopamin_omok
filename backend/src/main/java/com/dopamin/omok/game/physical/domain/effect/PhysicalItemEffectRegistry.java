package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 등록된 모든 {@link PhysicalItemEffect} 를 타입별로 모아 디스패치한다.
 * Spring 이 구현 빈을 전부 주입하므로 새 효과 추가 시 이 클래스는 수정 불필요(OCP).
 */
@Component
public class PhysicalItemEffectRegistry {

    private final Map<PhysicalItemType, PhysicalItemEffect> byType;

    public PhysicalItemEffectRegistry(List<PhysicalItemEffect> effects) {
        this.byType = effects.stream()
                .collect(Collectors.toUnmodifiableMap(PhysicalItemEffect::type, Function.identity()));
    }

    /** @return 효과가 적용됐는지(미등록 타입이거나 효과가 false면 false → 아이템 미소모). */
    public boolean apply(PhysicalItemType type, PhysicalGame game, PhysicalPlayer player, long now) {
        PhysicalItemEffect effect = byType.get(type);
        return effect != null && effect.apply(game, player, now);
    }
}
