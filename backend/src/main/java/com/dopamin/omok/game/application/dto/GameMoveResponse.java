package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.GameMove;
import com.dopamin.omok.game.domain.StoneColor;

/**
 * 착수 한 수. 라이브 브로드캐스트와 기보 조회가 함께 쓴다.
 *
 * 한 판이 수백 수까지 가므로 매 수에 반복되는 값은 담지 않는다 —
 * 방 코드는 클라이언트가 이미 알고 있고, 착수자는 닉네임만으로 표시하면 충분하다.
 *
 * @param playerNickname 착수자 닉네임(기보 목록 표시용)
 * @param color 돌 색상
 * @param row 착수한 행
 * @param col 착수한 열
 * @param moveNumber 몇 번째 수인지(판 안에서 유일)
 * @param soundAssetKey 착수자가 장착한 착수음 assetKey. 양쪽 클라이언트가 동일하게 재생(미장착 시 null)
 */
public record GameMoveResponse(
        String playerNickname,
        StoneColor color,
        Integer row,
        Integer col,
        Integer moveNumber,
        String soundAssetKey
) {
    public static GameMoveResponse from(GameMove move) {
        return from(move, null);
    }

    public static GameMoveResponse from(GameMove move, String soundAssetKey) {
        return new GameMoveResponse(
                move.getPlayer().getNickname(),
                move.getColor(),
                move.getRow(),
                move.getCol(),
                move.getMoveNumber(),
                soundAssetKey
        );
    }
}
