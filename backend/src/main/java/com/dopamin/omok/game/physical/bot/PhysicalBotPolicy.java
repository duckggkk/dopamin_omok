package com.dopamin.omok.game.physical.bot;

/**
 * 피지컬 오목 봇의 "두뇌" — 관측({@link BotObservation})을 받아 행동({@link BotAction})을 결정한다.
 *
 * ⭐ 이 인터페이스가 휴리스틱 ↔ 딥러닝 교체 지점이다.
 *  - 지금: {@link HeuristicPhysicalBotPolicy} (사람이 쓴 점수 규칙)
 *  - 나중: LearnedPhysicalBotPolicy 가 같은 인터페이스를 구현 — 학습된 모델을 호출(인프로세스 추론 또는
 *    파이썬 모델 서버 gRPC/HTTP)해 같은 BotAction 을 돌려주면, 드라이버/엔진/세션 코드는 그대로 둔다.
 *
 * 구현은 부수효과가 없어야 한다(관측만 읽고 행동만 반환). 실제 게임 상태 변경은 드라이버가 엔진을 통해 한다.
 */
public interface PhysicalBotPolicy {

    BotAction decide(BotObservation observation);
}
