package com.dopamin.omok.user.adapter.in.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 15, message = "닉네임은 2~15자 이어야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$",
                message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다.")
        String nickname,

        String profileImageUrl
) {}
