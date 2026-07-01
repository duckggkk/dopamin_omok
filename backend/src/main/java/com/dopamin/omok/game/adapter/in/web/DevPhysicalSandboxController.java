package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.port.in.StartPhysicalSandboxUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발 전용 도구 모음. {@code @Profile("local")} 이므로 dev(gradlew bootRun) 에서만 빈이 등록되고,
 * docker/prod 프로파일에서는 아예 존재하지 않아 외부로 노출되지 않는다.
 */
@Profile("local")
@RestController
@RequestMapping("/dev/physical")
@RequiredArgsConstructor
public class DevPhysicalSandboxController {

    private final StartPhysicalSandboxUseCase startPhysicalSandboxUseCase;

    /** 상대 없이 나 혼자 들어가는 피지컬 아레나를 즉시 시작(움직임 확인용). 생성된 방으로 입장하면 된다. */
    @PostMapping("/sandbox")
    public ResponseEntity<ApiResponse<RoomResponse>> startSandbox(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        RoomResponse response = startPhysicalSandboxUseCase.startPhysicalSandbox(userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("혼자 두기(피지컬) 샌드박스를 시작합니다.", response));
    }
}
