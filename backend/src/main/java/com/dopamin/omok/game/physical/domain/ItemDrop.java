package com.dopamin.omok.game.physical.domain;

/**
 * 필드에 떨어진 아이템(그리드 좌표 + 종류). 슬롯이 빈 캐릭터가 해당 칸에 진입하면 자동 획득된다.
 */
public record ItemDrop(int x, int y, PhysicalItemType type) {
}
