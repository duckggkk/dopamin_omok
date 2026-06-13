package com.dopamin.omok.plaza.adapter.in.web;

import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.plaza.application.PlazaSessionManager;
import com.dopamin.omok.plaza.application.dto.PlazaJoinResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 광장 채널 배정(REST). 인증 필요(SecurityConfig anyRequest().authenticated()).
 * 클라는 받은 channelId 토픽을 구독한 뒤 WS /app/plaza/{channelId}/join 으로 실제 입장한다.
 */
@RestController
@RequestMapping("/plaza")
@RequiredArgsConstructor
public class PlazaRestController {

    private final PlazaSessionManager manager;

    @PostMapping("/join")
    public ApiResponse<PlazaJoinResponse> join() {
        return ApiResponse.success(manager.allocate());
    }
}
