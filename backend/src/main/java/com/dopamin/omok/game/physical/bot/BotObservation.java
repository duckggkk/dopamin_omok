package com.dopamin.omok.game.physical.bot;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;

import java.util.List;

/**
 * 봇이 "보는" 한 시점의 게임 상태(읽기 전용 관측값). 정책({@link PhysicalBotPolicy})의 입력이다.
 *
 * 이 레코드가 "추상화의 핵심"이다 — 휴리스틱이든 딥러닝이든 정책은 오직 이 관측값만 받고
 * {@link BotAction} 만 돌려준다. 그래서 나중에 학습 모델로 교체할 때 도메인(엔진/세션)은 손대지 않는다.
 * 딥러닝 정책이라면 이 관측값을 그대로 신경망 입력 텐서로 인코딩하면 된다(보드 그리드 + 좌표 + 쿨다운 플래그).
 *
 * 좌표계: x=열, y=행. cells 는 [y][x] 이며 0=빈칸, 1=흑, 2=백, 3=분화구(이동/착수 불가).
 */
public record BotObservation(
        int boardSize,
        int winCount,
        int[][] cells,
        StoneColor self,
        int selfX,
        int selfY,
        int oppX,
        int oppY,
        boolean canPlace,      // 착수 쿨다운 해제됨
        boolean canDestroy,    // 파괴 쿨다운 해제됨
        boolean speedBoosted,  // 이동 부스트 적용 중
        PhysicalItemType heldItem, // 보유 아이템(없으면 null)
        List<ItemView> items       // 필드에 떨어진 아이템들
) {
    public record ItemView(int x, int y, PhysicalItemType type) {}

    /** 보드 인코딩과 일치: 흑=1, 백=2. */
    public int selfCode() {
        return self == StoneColor.BLACK ? 1 : 2;
    }

    public int oppCode() {
        return self == StoneColor.BLACK ? 2 : 1;
    }
}
