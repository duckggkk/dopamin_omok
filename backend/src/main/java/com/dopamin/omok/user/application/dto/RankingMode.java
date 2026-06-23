package com.dopamin.omok.user.application.dto;

/**
 * 랭킹 탭. 정렬 기준과 표시 전적이 함께 달라진다.
 *  - TOTAL    : 통합(기존) — 승수순, 통합 전적
 *  - CLASSIC  : 일반 레이팅순, 일반 전적
 *  - PHYSICAL : 피지컬 레이팅순, 피지컬 전적
 */
public enum RankingMode {
    TOTAL, CLASSIC, PHYSICAL
}
