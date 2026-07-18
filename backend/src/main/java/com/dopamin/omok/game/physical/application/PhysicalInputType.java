package com.dopamin.omok.game.physical.application;

/**
 * 클라이언트가 보내는 피지컬 오목 입력 종류. 단일 입력 엔드포인트로 받아 확장이 쉽다.
 * MOVE_START 만 방향(Direction)을 동반한다.
 */
public enum PhysicalInputType {
    MOVE_START,  // 방향키 누름 → 이동 의도 시작
    MOVE_STOP,   // 방향키 뗌 → 이동 정지
    PLACE,       // Space → 현재 칸 착수
    DESTROY,     // X → 현재 칸 상대 돌 파괴
    USE_ITEM     // C → 보유 아이템 사용
}
