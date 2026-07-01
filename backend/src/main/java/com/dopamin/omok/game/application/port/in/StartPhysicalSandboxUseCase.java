package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

/**
 * 로컬 개발 전용 '피지컬 혼자 두기' 시작 — 상대(사람·봇) 없이 나 혼자 아레나에 들어가
 * 캐릭터 이동/착수/파괴/아이템을 눈으로 확인하기 위한 샌드박스.
 * 일반 피지컬 대국 인프라(방·게임·실시간 세션)를 그대로 재사용하되 참가자가 1명이라는 점만 다르다.
 * 캐주얼(레이팅 미반영)로 만들어 전적/레이팅에 영향이 없다. 노출 엔드포인트는 local 프로파일에서만 등록된다.
 */
public interface StartPhysicalSandboxUseCase {
    RoomResponse startPhysicalSandbox(Long userId);
}
