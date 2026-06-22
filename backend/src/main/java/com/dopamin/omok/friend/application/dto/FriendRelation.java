package com.dopamin.omok.friend.application.dto;

/** 나와 상대의 관계. 프로필에서 버튼 상태(친구추가/요청됨/수락/친구)를 정하는 데 쓴다. */
public enum FriendRelation {
    SELF,             // 나 자신
    NONE,             // 아무 관계 없음
    REQUEST_SENT,     // 내가 보낸 요청 대기 중
    REQUEST_RECEIVED, // 내가 받은 요청 대기 중
    FRIENDS           // 이미 친구
}
