package com.dopamin.omok.user.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.dto.AiProgressResponse;
import com.dopamin.omok.user.application.port.in.AiProgressUseCase;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiProgressService implements AiProgressUseCase {

    /** 싱글플레이 AI 사다리 총 단계 수. 프론트 omokAi.ts 의 AI_LEVELS 길이와 반드시 일치해야 한다. */
    public static final int MAX_LEVEL = 7;

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;

    @Override
    public AiProgressResponse getProgress(Long meId) {
        User me = loadUserPort.findById(meId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        return new AiProgressResponse(me.getAiClearedLevel(), MAX_LEVEL);
    }

    @Override
    @Transactional
    public AiProgressResponse recordClear(Long meId, int level) {
        User me = loadUserPort.findById(meId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        me.recordAiClear(level); // 도메인이 "다음 단계만 전진" 무결성 규칙 적용
        saveUserPort.save(me);
        return new AiProgressResponse(me.getAiClearedLevel(), MAX_LEVEL);
    }
}
