package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.ChatMessageResponse;

public interface SendChatMessageUseCase {
    /**
     * 방 참가자가 보낸 채팅을 검증하고 브로드캐스트용 메시지로 만든다.
     * 발신자가 참가자인지 관전자인지에 따라 표시할 돌 색이 달라진다.
     *
     * @param senderNickname 인증 토큰에서 읽은 발신자 닉네임(표시 전용)
     */
    ChatMessageResponse sendChatMessage(String roomCode, Long userId, String senderNickname, String content);
}
