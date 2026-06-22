package com.dopamin.omok.friend.application.service;

import com.dopamin.omok.friend.application.dto.FriendRelation;
import com.dopamin.omok.friend.application.dto.FriendRequestResponse;
import com.dopamin.omok.friend.application.dto.FriendResponse;
import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.application.dto.RelationResponse;
import com.dopamin.omok.friend.application.port.in.FriendUseCase;
import com.dopamin.omok.friend.application.port.out.DeleteFriendshipPort;
import com.dopamin.omok.friend.application.port.out.LoadFriendshipPort;
import com.dopamin.omok.friend.application.port.out.LoadHeadToHeadPort;
import com.dopamin.omok.friend.application.port.out.SaveFriendshipPort;
import com.dopamin.omok.friend.domain.Friendship;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService implements FriendUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveFriendshipPort saveFriendshipPort;
    private final LoadFriendshipPort loadFriendshipPort;
    private final DeleteFriendshipPort deleteFriendshipPort;
    private final LoadHeadToHeadPort loadHeadToHeadPort;

    @Override
    @Transactional
    public void sendRequest(Long meId, String targetNickname) {
        User me = loadUserPort.findById(meId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        User target = loadUserPort.findByNickname(targetNickname)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        if (target.getId().equals(meId)) {
            throw new OmokException(ErrorCode.CANNOT_FRIEND_SELF);
        }
        loadFriendshipPort.findBetween(meId, target.getId()).ifPresent(existing -> {
            throw new OmokException(existing.isAccepted()
                    ? ErrorCode.ALREADY_FRIENDS
                    : ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        });
        saveFriendshipPort.save(Friendship.request(me, target));
    }

    @Override
    @Transactional
    public void acceptRequest(Long meId, UUID requesterPublicId) {
        User requester = loadUserPort.findByPublicId(requesterPublicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        Friendship friendship = loadFriendshipPort.findBetween(meId, requester.getId())
                .orElseThrow(() -> new OmokException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        // 내가 받은(수신자=나) 대기 중 요청만 수락 가능
        if (!friendship.isPending() || !friendship.isAddressee(meId)) {
            throw new OmokException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        friendship.accept();
        saveFriendshipPort.save(friendship);
    }

    @Override
    @Transactional
    public void removeRelation(Long meId, UUID otherPublicId) {
        User other = loadUserPort.findByPublicId(otherPublicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        Friendship friendship = loadFriendshipPort.findBetween(meId, other.getId())
                .orElseThrow(() -> new OmokException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        deleteFriendshipPort.delete(friendship);
    }

    @Override
    public List<FriendResponse> getFriends(Long meId) {
        return loadFriendshipPort.findAcceptedOf(meId).stream()
                .map(f -> toFriendResponse(meId, f.other(meId)))
                .toList();
    }

    @Override
    public List<FriendRequestResponse> getIncomingRequests(Long meId) {
        return loadFriendshipPort.findIncomingPendingOf(meId).stream()
                .map(f -> {
                    User r = f.getRequester();
                    return new FriendRequestResponse(
                            r.getPublicId(), r.getNickname(), r.getProfileImageUrl(), f.getCreatedAt());
                })
                .toList();
    }

    @Override
    public RelationResponse getRelation(Long meId, UUID otherPublicId) {
        User other = loadUserPort.findByPublicId(otherPublicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        if (other.getId().equals(meId)) {
            return new RelationResponse(FriendRelation.SELF, HeadToHead.empty());
        }
        FriendRelation relation = loadFriendshipPort.findBetween(meId, other.getId())
                .map(f -> {
                    if (f.isAccepted()) return FriendRelation.FRIENDS;
                    return f.isRequester(meId) ? FriendRelation.REQUEST_SENT : FriendRelation.REQUEST_RECEIVED;
                })
                .orElse(FriendRelation.NONE);
        HeadToHead head = loadHeadToHeadPort.between(meId, other.getId());
        return new RelationResponse(relation, head);
    }

    private FriendResponse toFriendResponse(Long meId, User other) {
        HeadToHead head = loadHeadToHeadPort.between(meId, other.getId());
        return new FriendResponse(
                other.getPublicId(), other.getNickname(), other.getProfileImageUrl(),
                other.getClassicRating(), other.getPhysicalRating(), head);
    }
}
