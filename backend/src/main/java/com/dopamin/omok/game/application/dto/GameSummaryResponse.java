package com.dopamin.omok.game.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.user.domain.User;

/**
 * 전적 목록용 요약 응답. 목록에서 쓰지 않는 진행 중 정보(현재 턴·마지막 착수 시각)와
 * 연출용 필드는 빼고, 플레이어도 닉네임·프로필까지만 담아 응답을 가볍게 유지한다.
 * 상세 화면은 {@link GameResponse} 를 그대로 쓴다.
 *
 * @param id 게임 식별자
 * @param gameNumber 방 안에서의 게임 회차
 * @param status 게임 상태
 * @param gameType 게임 종류(클래식/피지컬 등)
 * @param ranked 랭크 게임 여부
 * @param blackPlayer 흑돌 플레이어 요약
 * @param whitePlayer 백돌 플레이어 요약
 * @param winner 승자 요약. 무승부·중단이면 null
 * @param startedAt 게임 시작 시각
 * @param finishedAt 게임 종료 시각
 */
public record GameSummaryResponse(
        Long id,
        Integer gameNumber,
        GameStatus status,
        GameType gameType,
        boolean ranked,
        PlayerSummary blackPlayer,
        PlayerSummary whitePlayer,
        PlayerSummary winner,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {

    public static GameSummaryResponse from(Game game) {
        return new GameSummaryResponse(
                game.getId(),
                game.getGameNumber(),
                game.getStatus(),
                game.getRoom().getGameType(),
                game.getRoom().isRanked(),
                PlayerSummary.from(game.getBlackPlayer()),
                PlayerSummary.from(game.getWhitePlayer()),
                PlayerSummary.from(game.getWinner()),
                game.getStartedAt(),
                game.getFinishedAt()
        );
    }

    /**
     * 목록에 노출할 플레이어 최소 정보. 내부 PK 대신 외부 노출용 publicId 를 쓴다.
     *
     * @param id 외부 노출용 사용자 UUID
     * @param nickname 닉네임
     * @param profileImageUrl 프로필 이미지 주소. 미설정이면 null
     */
    public record PlayerSummary(
            UUID id,
            String nickname,
            String profileImageUrl
    ) {

        public static PlayerSummary from(User user) {
            if (user == null) {
                return null;
            }

            return new PlayerSummary(
                    user.getPublicId(),
                    user.getNickname(),
                    user.getProfileImageUrl()
            );
        }
    }
}
