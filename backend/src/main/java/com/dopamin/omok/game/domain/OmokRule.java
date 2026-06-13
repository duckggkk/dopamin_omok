package com.dopamin.omok.game.domain;

/**
 * 클래식 오목의 규칙 변형.
 * <ul>
 *   <li>{@code FREESTYLE} — 자유룰. 흑/백 모두 제한 없이 정확히 5목을 먼저 만들면 승리(6목 이상은 무효).</li>
 *   <li>{@code RENJU} — 렌주룰. 흑(선)에게 금수(3-3, 4-4, 장목=6목 이상)를 적용한다. 백은 제한이 없다.</li>
 * </ul>
 * 피지컬 오목에는 적용되지 않는다(항상 FREESTYLE 로 취급).
 */
public enum OmokRule {
    FREESTYLE,
    RENJU
}
