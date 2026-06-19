package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.physical.domain.Direction;

/**
 * 봇 정책이 한 틱에 내리는 결정. 기존 입력 어휘(이동/착수/파괴/아이템)와 1:1로 대응된다 —
 * {@link PhysicalBotDriver} 가 이걸 엔진 호출로 번역한다. 사람 입력과 같은 통로를 타므로
 * 봇이라고 규칙을 우회하지 않는다(쿨다운/범위 검증은 엔진이 동일하게 적용).
 *
 * MOVE 만 방향을 동반한다. IDLE 은 "이번 틱은 아무것도 안 함(이동 정지)".
 */
public record BotAction(Kind kind, Direction direction) {

    public enum Kind { MOVE, PLACE, DESTROY, USE_ITEM, IDLE }

    public static BotAction move(Direction direction) {
        return new BotAction(Kind.MOVE, direction);
    }

    public static BotAction place() {
        return new BotAction(Kind.PLACE, null);
    }

    public static BotAction destroy() {
        return new BotAction(Kind.DESTROY, null);
    }

    public static BotAction useItem() {
        return new BotAction(Kind.USE_ITEM, null);
    }

    public static BotAction idle() {
        return new BotAction(Kind.IDLE, null);
    }
}
