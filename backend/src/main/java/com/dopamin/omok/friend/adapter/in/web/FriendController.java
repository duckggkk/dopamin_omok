package com.dopamin.omok.friend.adapter.in.web;

import com.dopamin.omok.friend.adapter.in.web.dto.SendFriendRequestRequest;
import com.dopamin.omok.friend.application.dto.FriendRequestResponse;
import com.dopamin.omok.friend.application.dto.FriendResponse;
import com.dopamin.omok.friend.application.dto.RelationResponse;
import com.dopamin.omok.friend.application.port.in.FriendUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendUseCase friendUseCase;

    /** 내 친구 목록(상대 전적 포함). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(friendUseCase.getFriends(me.getId())));
    }

    /** 내가 받은 친구 요청 목록. */
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> getIncomingRequests(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(friendUseCase.getIncomingRequests(me.getId())));
    }

    /** 닉네임으로 친구 요청 보내기. */
    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<Void>> sendRequest(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody SendFriendRequestRequest request) {
        friendUseCase.sendRequest(me.getId(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("친구 요청을 보냈습니다."));
    }

    /** 그 사람이 보낸 요청 수락. */
    @PostMapping("/requests/{publicId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID publicId) {
        friendUseCase.acceptRequest(me.getId(), publicId);
        return ResponseEntity.ok(ApiResponse.success("친구 요청을 수락했습니다."));
    }

    /** 관계 제거 — 요청 거절/취소 또는 친구 끊기. */
    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> removeRelation(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID publicId) {
        friendUseCase.removeRelation(me.getId(), publicId);
        return ResponseEntity.ok(ApiResponse.success("처리되었습니다."));
    }

    /** 나와 그 사람의 관계 + 상대 전적(프로필용). */
    @GetMapping("/relation/{publicId}")
    public ResponseEntity<ApiResponse<RelationResponse>> getRelation(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID publicId) {
        return ResponseEntity.ok(ApiResponse.success(friendUseCase.getRelation(me.getId(), publicId)));
    }
}
