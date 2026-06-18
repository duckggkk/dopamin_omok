package com.dopamin.omok.game.adapter.in.web.dto;

public record ChangeStoneSkinRequest(
        // null 이면 기본 스킨으로 되돌림(장착 해제)
        Long itemId
) {}
