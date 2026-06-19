package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.domain.StoneColor;

/**
 * 드라이버가 한 봇에 대해 이번 틱에 실제로 적용한 결정(로깅용 반환값).
 * x,y 는 '행동 직전' 봇 위치 — 학습 로그에서 (상태→행동) 라벨의 상태 앵커로 쓴다.
 */
public record BotDecision(StoneColor color, BotAction action, int x, int y) {
}
