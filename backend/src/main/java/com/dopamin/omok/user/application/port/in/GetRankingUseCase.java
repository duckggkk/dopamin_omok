package com.dopamin.omok.user.application.port.in;

import com.dopamin.omok.user.application.dto.RankingMode;
import com.dopamin.omok.user.application.dto.RankingResponse;

import java.util.List;

public interface GetRankingUseCase {
    /** 선택한 모드(통합/일반/피지컬)의 상위 limit명 랭킹(1-based rank 포함). */
    List<RankingResponse> getRanking(int limit, RankingMode mode);
}
