package com.dopamin.omok.user.application.dto;

/**
 * 싱글플레이 AI 사다리 진척 응답.
 *
 * @param clearedLevel 계정에 저장된, 클리어한 최고 단계(0 = 미클리어)
 * @param maxLevel     사다리 총 단계 수(프론트 LEVELS 와 일치)
 */
public record AiProgressResponse(int clearedLevel, int maxLevel) {
}
