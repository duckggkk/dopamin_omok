package com.dopamin.omok.friend.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 친구 요청 보내기 — 대상 닉네임. */
public record SendFriendRequestRequest(
        @NotBlank(message = "닉네임을 입력해주세요.")
        String nickname
) {}
