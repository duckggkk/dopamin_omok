package com.dopamin.omok.friend.application.port.in;

import com.dopamin.omok.friend.application.dto.FriendRequestResponse;
import com.dopamin.omok.friend.application.dto.FriendResponse;
import com.dopamin.omok.friend.application.dto.RelationResponse;

import java.util.List;
import java.util.UUID;

public interface FriendUseCase {
    /** 닉네임으로 친구 요청 보내기. */
    void sendRequest(Long meId, String targetNickname);

    /** 그 사람(요청자)이 보낸 요청을 수락. */
    void acceptRequest(Long meId, UUID requesterPublicId);

    /** 관계 제거 — 요청 거절/취소/친구 끊기 모두 처리(나와 상대 사이의 행 삭제). */
    void removeRelation(Long meId, UUID otherPublicId);

    /** 내 친구 목록(상대 전적 포함). */
    List<FriendResponse> getFriends(Long meId);

    /** 내가 받은 친구 요청 목록. */
    List<FriendRequestResponse> getIncomingRequests(Long meId);

    /** 나와 그 사람의 관계 + 상대 전적(프로필용). */
    RelationResponse getRelation(Long meId, UUID otherPublicId);
}
