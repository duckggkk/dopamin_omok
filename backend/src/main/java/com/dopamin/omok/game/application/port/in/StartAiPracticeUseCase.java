package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

/**
 * 피지컬 오목 'AI 연습' 시작 — 혼자서 예약 봇 계정과 즉시 한 판.
 * 일반 피지컬 대국 인프라(방·게임·실시간 세션·리플레이)를 그대로 재사용하되,
 * 봇과의 대국이라 레이팅·전적에는 반영되지 않는다(RoomService 가 집계를 건너뜀).
 */
public interface StartAiPracticeUseCase {
    RoomResponse startAiPractice(Long userId);
}
