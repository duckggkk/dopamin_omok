package com.dopamin.omok.game.physical.domain.effect;

import com.dopamin.omok.game.physical.domain.PhysicalGame;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import com.dopamin.omok.game.physical.domain.PhysicalPlayer;

/**
 * 아이템 사용 효과 전략. 새 아이템은 이 인터페이스 구현 @Component 하나만 추가하면
 * {@link PhysicalItemEffectRegistry} 가 자동 수집한다(OCP — 기존 코드 수정 불필요).
 */
public interface PhysicalItemEffect {

    PhysicalItemType type();

    /**
     * {@code player} 가 자신의 현재 위치/상태에서 효과를 발동한다. {@code now}=epoch ms.
     * @return 효과가 실제로 적용됐는지. false면 아이템 슬롯을 비우지 않는다
     *         (예: 제거할 상대 돌이 없을 때 REMOVE_STONE 유지).
     */
    boolean apply(PhysicalGame game, PhysicalPlayer player, long now);
}
