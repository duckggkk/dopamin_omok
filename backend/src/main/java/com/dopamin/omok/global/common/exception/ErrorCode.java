package com.dopamin.omok.global.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀렸습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다. 이메일을 확인해주세요."),
    VERIFICATION_TOKEN_NOT_FOUND(HttpStatus.BAD_REQUEST, "유효하지 않은 인증 토큰입니다."),
    VERIFICATION_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "인증 토큰이 만료되었습니다. 재발송을 요청해주세요."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 시도 횟수를 초과했습니다. 재발송을 요청해주세요."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "이미 인증된 이메일입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),
    OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "소셜 계정의 이메일이 인증되지 않았습니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "소셜 로그인 처리에 실패했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PROFILE_PRIVATE(HttpStatus.FORBIDDEN, "비공개 프로필입니다."),

    // Room
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    ROOM_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "이미 폐쇄된 방입니다."),
    ROOM_ALREADY_FULL(HttpStatus.BAD_REQUEST, "방에 참가자가 가득 찼습니다."),
    ROOM_SPECTATOR_FULL(HttpStatus.BAD_REQUEST, "관전 인원이 가득 찼습니다."),
    ROOM_NOT_HOST(HttpStatus.FORBIDDEN, "방장만 수행할 수 있습니다."),
    ALREADY_IN_ROOM(HttpStatus.BAD_REQUEST, "이미 방에 참가 중입니다."),
    NOT_IN_ROOM(HttpStatus.BAD_REQUEST, "방에 참가 중이 아닙니다."),
    REMATCH_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "리매치를 요청할 수 없는 상태입니다."),
    NOT_ENOUGH_PLAYERS(HttpStatus.BAD_REQUEST, "아직 상대방이 입장하지 않았습니다."),
    PLAYER_NOT_READY(HttpStatus.BAD_REQUEST, "상대방이 아직 준비 완료하지 않았습니다."),
    DUPLICATE_STONE_SKIN(HttpStatus.BAD_REQUEST, "두 플레이어가 같은 바둑알 스킨을 장착해 게임을 시작할 수 없습니다."),
    STONE_SKIN_CHANGE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "대기 중에만 바둑알 스킨을 바꿀 수 있습니다."),

    // Game
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다."),
    GAME_NOT_IN_PROGRESS(HttpStatus.BAD_REQUEST, "진행 중인 게임이 아닙니다."),
    GAME_NOT_YOUR_TURN(HttpStatus.BAD_REQUEST, "당신의 차례가 아닙니다."),
    INVALID_MOVE(HttpStatus.BAD_REQUEST, "유효하지 않은 수입니다."),
    POSITION_ALREADY_OCCUPIED(HttpStatus.BAD_REQUEST, "이미 돌이 놓인 위치입니다."),
    RENJU_FORBIDDEN_MOVE(HttpStatus.BAD_REQUEST, "렌주룰: 금수(3-3·4-4·장목) 자리입니다. 다른 곳에 두세요."),
    NOT_GAME_PARTICIPANT(HttpStatus.FORBIDDEN, "게임 참가자가 아닙니다."),
    PLAYER_TIMEOUT(HttpStatus.BAD_REQUEST, "시간 초과로 패배 처리되었습니다."),

    // Shop
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "아이템을 찾을 수 없습니다."),
    ITEM_NOT_OWNED(HttpStatus.FORBIDDEN, "보유하지 않은 아이템입니다."),
    INSUFFICIENT_CURRENCY(HttpStatus.BAD_REQUEST, "돌이 부족합니다."),
    DIRECT_CHARGE_DISABLED(HttpStatus.FORBIDDEN, "현재 충전 기능을 사용할 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    // Friend
    CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 친구 요청을 보낼 수 없습니다."),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "이미 친구입니다."),
    FRIEND_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 친구 요청이 진행 중입니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없습니다."),
    FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
