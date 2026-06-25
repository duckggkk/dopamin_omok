package com.dopamin.omok.user.domain;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 칭호 — 특정 업적을 달성하면 자동으로 부여되는 표식.
 * 별도 테이블에 저장하지 않고 {@link User} 상태에서 파생한다(조건 = Predicate). 새 칭호는 상수 한 줄만 추가하면 된다.
 * 표시 우선순위는 enum 선언 순서(앞쪽이 대표 칭호).
 */
public enum Title {

    /** 클래식 오목 AI 최종 단계(7단계) 클리어. */
    AI_CRUSHER("인공지능 분쇄자", "클래식 오목 AI 최종 단계(7단계)를 정복한 증표",
            user -> user.getAiClearedLevel() >= 7);

    private final String displayName;
    private final String description;
    private final Predicate<User> condition;

    Title(String displayName, String description, Predicate<User> condition) {
        this.displayName = displayName;
        this.description = description;
        this.condition = condition;
    }

    public String getKey() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEarnedBy(User user) {
        return user != null && condition.test(user);
    }

    /** 유저가 획득한 칭호 목록(미획득 제외). 선언 순서 = 표시 우선순위. */
    public static List<Title> earnedBy(User user) {
        return Arrays.stream(values()).filter(t -> t.isEarnedBy(user)).toList();
    }
}
