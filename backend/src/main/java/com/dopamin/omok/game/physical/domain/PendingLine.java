package com.dopamin.omok.game.physical.domain;

import com.dopamin.omok.game.domain.StoneColor;

import java.util.List;

/**
 * 충전 중인(확정 대기 중인) 완성 오목 한 줄. 오목을 완성하면 즉시 점수가 되지 않고
 * lockAt 까지 '게이지'가 차야 파괴+득점된다. 그 사이 상대가 라인의 돌을 끊으면 무효가 된다.
 * 한 판에 여러 줄이 동시에 충전될 수 있다(서로 다른 줄은 독립 게이지).
 *
 * @param color 이 줄을 완성한 색
 * @param cells 줄을 이루는 칸 좌표들([x,y], winCount개 이상)
 * @param lockAt 충전 완료(파괴+득점) 시각(epoch ms)
 */
public record PendingLine(StoneColor color, List<int[]> cells, long lockAt) {
}
