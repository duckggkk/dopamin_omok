package com.dopamin.omok.game.physical.domain;

/**
 * 피지컬 오목 인게임 아이템 종류. 새 아이템은 여기에 값을 추가하고
 * 대응하는 {@code PhysicalItemEffect} 구현 + application.yml 가중치 한 줄만 더하면 된다(OCP).
 */
public enum PhysicalItemType {
    /** 일정 시간 이동 쿨다운 단축. */
    SPEED_BOOST,
    /** 현재 칸의 돌을 파괴하고 그 칸을 영구 착수 불가(분화구)로 만든다. */
    CRATER,
    /** 내 주변 3x3 안의 모든 돌(내 돌+상대 돌)을 제거한다. */
    BOMB,
    /** 나와 가장 가까운 상대 돌 하나를 어디서든 제거한다(게임 시작 시 1개 보유). */
    REMOVE_STONE
}
