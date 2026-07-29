package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.user.application.dto.ModeStats;

import java.util.Collection;
import java.util.Map;

/**
 * 끝난 대국(games) 기록에서 사용자의 모드별 전적을 집계한다.
 * 통합 전적은 users 테이블 컬럼(wins/losses/draws)을 그대로 쓰므로 여기서 다루지 않는다.
 */
public interface LoadUserGameStatsPort {

    /**
     * 해당 모드(일반/피지컬)의 승/패/무를 게임 기록에서 집계한다.
     * 방의 랭크/캐주얼 표시와 무관하게 회원 대 회원 대국을 모두 센다(봇·게스트 대국 제외).
     */
    ModeStats statsByMode(Long userId, GameType mode);

    /**
     * 여러 사용자의 모드별 전적을 한 번에 집계한다(랭킹처럼 N명을 함께 보여줄 때).
     * 전적이 한 판도 없는 사용자는 결과 맵에 키가 없으므로 호출부에서 기본값을 채운다.
     */
    Map<Long, ModeStats> statsByModeForUsers(Collection<Long> userIds, GameType mode);
}
