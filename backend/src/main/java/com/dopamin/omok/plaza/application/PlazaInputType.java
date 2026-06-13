package com.dopamin.omok.plaza.application;

/**
 * 클라이언트가 보내는 광장 입력 종류(단일 엔드포인트 → 확장 용이).
 * MOVE_START 만 방향(Direction)을 동반한다. EMOTE/춤 등은 Phase 2 확장 자리.
 */
public enum PlazaInputType {
    MOVE_START, // 방향키 누름 → 이동 의도 시작
    MOVE_STOP   // 방향키 뗌 → 이동 정지
}
