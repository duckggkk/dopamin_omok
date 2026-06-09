package com.dopamin.omok.user.application.port.in;

import com.dopamin.omok.user.application.dto.RankingResponse;

import java.util.List;

public interface GetRankingUseCase {
    /** 상위 limit명 랭킹(1-based rank 포함). */
    List<RankingResponse> getRanking(int limit);
}
