package com.dopamin.omok.user.application.port.in;

import com.dopamin.omok.user.application.dto.AiProgressResponse;

/**
 * 싱글플레이 AI 사다리 진척 조회/기록.
 * 진척은 계정(서버)에 저장되어 기기를 바꿔도 이어진다. AI 대국 자체는 클라이언트에서만
 * 계산되며 레이팅과 무관하다 — 서버는 "어디까지 깼는지"만 보관한다.
 */
public interface AiProgressUseCase {

    /** 내 현재 진척(클리어 단계 + 총 단계 수). */
    AiProgressResponse getProgress(Long meId);

    /**
     * 한 단계를 클리어했음을 보고한다. 도메인이 "다음 단계만 전진" 규칙으로 검증하므로
     * 단계를 건너뛴 보고는 무시되고 현재 진척이 그대로 반환된다.
     */
    AiProgressResponse recordClear(Long meId, int level);
}
