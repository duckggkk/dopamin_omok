package com.dopamin.omok.user.application.dto;

import com.dopamin.omok.user.domain.Title;
import com.dopamin.omok.user.domain.User;

import java.util.List;

/**
 * 칭호 응답(키 + 표시명 + 설명). 프로필/플레이어 응답에 함께 실려 클라이언트가 뱃지로 표시한다.
 * 목록의 첫 항목이 대표 칭호(선언 우선순위).
 */
public record TitleResponse(String key, String name, String description) {

    public static TitleResponse from(Title title) {
        return new TitleResponse(title.getKey(), title.getDisplayName(), title.getDescription());
    }

    /** 유저가 획득한 칭호를 응답 목록으로. */
    public static List<TitleResponse> earnedBy(User user) {
        return Title.earnedBy(user).stream().map(TitleResponse::from).toList();
    }
}
