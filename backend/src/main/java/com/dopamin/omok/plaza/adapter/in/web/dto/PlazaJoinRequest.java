package com.dopamin.omok.plaza.adapter.in.web.dto;

import com.dopamin.omok.plaza.domain.PlazaAppearance;

/** WS 입장 페이로드. 자신의 아바타 외형을 함께 전달(없으면 기본 외형). */
public record PlazaJoinRequest(PlazaAppearance appearance) {
}
